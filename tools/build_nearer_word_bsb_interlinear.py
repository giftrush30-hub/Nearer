#!/usr/bin/env python3
"""Build a public-domain BSB text pack and exact interlinear-to-phrase alignments."""
from __future__ import annotations
import argparse, hashlib, json, re, time, urllib.error, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

BASE="https://bible.helloao.org/api"
ATTRIBUTION=("Berean Standard Bible text and interlinear tables are dedicated to the public domain. "
             "Source: Berean Bible / Free Use Bible API. NeaRer matched BSB English gloss phrases to verse tokens.")
PUNCTUATION=' ,.;:!?()[]"“”'
EXPECTED_BOOKS=[
    ("GEN",50),("EXO",40),("LEV",27),("NUM",36),("DEU",34),("JOS",24),("JDG",21),("RUT",4),
    ("1SA",31),("2SA",24),("1KI",22),("2KI",25),("1CH",29),("2CH",36),("EZR",10),("NEH",13),
    ("EST",10),("JOB",42),("PSA",150),("PRO",31),("ECC",12),("SNG",8),("ISA",66),("JER",52),
    ("LAM",5),("EZK",48),("DAN",12),("HOS",14),("JOL",3),("AMO",9),("OBA",1),("JON",4),
    ("MIC",7),("NAM",3),("HAB",3),("ZEP",3),("HAG",2),("ZEC",14),("MAL",4),("MAT",28),
    ("MRK",16),("LUK",24),("JHN",21),("ACT",28),("ROM",16),("1CO",16),("2CO",13),("GAL",6),
    ("EPH",6),("PHP",4),("COL",4),("1TH",5),("2TH",3),("1TI",6),("2TI",4),("TIT",3),
    ("PHM",1),("HEB",13),("JAS",5),("1PE",5),("2PE",3),("1JN",5),("2JN",1),("3JN",1),
    ("JUD",1),("REV",22)
]

def fetch_json(url, retries=4):
    last=None
    for attempt in range(retries):
        try:
            req=urllib.request.Request(url,headers={"User-Agent":"NeaRer-Word-Offline-Bible-Pack-Builder/1.0"})
            with urllib.request.urlopen(req,timeout=45) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code==404: return None
            last=e
        except Exception as e:
            last=e
        time.sleep(min(2**attempt,8))
    raise RuntimeError(f"Could not fetch {url}: {last}")

def canonical_strong(raw):
    if not raw: return None
    value=str(raw).strip().upper()
    m=re.fullmatch(r"([GH])0*(\d+)[A-Z]?",value)
    if not m: return None
    return m.group(1)+str(int(m.group(2)))

def verse_items(chapter_json):
    chapter=chapter_json.get("chapter",{})
    content=chapter.get("content",[])
    out={}
    for item in content:
        if item.get("type")=="verse":
            try: number=int(item["number"])
            except (KeyError,TypeError,ValueError): continue
            text=item.get("text","")
            if isinstance(text,str) and text.strip(): out[number]=text
    return out

def interlinear_verse_items(interlinear_json):
    content=interlinear_json.get("chapter",{}).get("content",[])
    out={}
    for item in content:
        try: verse_no=int(item["verse"])
        except (KeyError,TypeError,ValueError): continue
        words=item.get("words",[])
        if isinstance(words,list): out[verse_no]=words
    return out

def normalize_token(value):
    return re.sub(r"^[^\w]+|[^\w]+$","",value,flags=re.UNICODE).casefold()

def alignment_rows(book_id, chapter_no, verse_no, text, words):
    tokens=list(re.finditer(r"\S+",text))
    normalized=[normalize_token(m.group()) for m in tokens]
    rows=[]; seen=set(); cursor=0; matched=0; eligible=0
    last_rank=None; last_gloss_tokens=None; last_span=None
    ordered=[]
    for word in words:
        try: rank=int(word.get("bsbPos"))
        except (TypeError,ValueError): continue
        if rank>0: ordered.append((rank,word))
    ordered.sort(key=lambda item:item[0])
    for rank,word in ordered:
        raw_strongs=word.get("strongs") or word.get("strong") or []
        if isinstance(raw_strongs,str): raw_strongs=[raw_strongs]
        strongs=list(dict.fromkeys(filter(None,(canonical_strong(value) for value in raw_strongs))))
        gloss=word.get("gloss")
        if not strongs or not isinstance(gloss,str) or not gloss.strip(): continue
        gloss_tokens=[normalize_token(x) for x in re.findall(r"\S+",gloss)]
        gloss_tokens=[x for x in gloss_tokens if x]
        if not gloss_tokens: continue
        eligible+=1
        if rank==last_rank and gloss_tokens==last_gloss_tokens and last_span is not None:
            found,end=last_span
        else:
            found=None
            for start in range(cursor,max(cursor,len(tokens)-len(gloss_tokens)+1)):
                if normalized[start:start+len(gloss_tokens)]==gloss_tokens:
                    found=start; break
            if found is None: continue
            end=found+len(gloss_tokens)
            cursor=max(cursor,end)
            last_rank=rank; last_gloss_tokens=gloss_tokens; last_span=(found,end)
        matched+=1
        for token_index in range(found,end):
            surface=tokens[token_index].group().strip(PUNCTUATION)
            if not surface: continue
            for strong in strongs:
                identity=(book_id,chapter_no,verse_no,token_index,surface,strong)
                if identity in seen: continue
                seen.add(identity)
                rows.append({
                    "translationId":"BSB","bookId":book_id,"chapter":chapter_no,
                    "verse":verse_no,"tokenIndex":token_index,"surfaceForm":surface,
                    "lemmaId":None,"strongNumber":strong,"morphologyCode":None
                })
    return rows,eligible,matched

def process_chapter(book, chapter_no):
    book_id=book["id"]
    chapter=fetch_json(f"{BASE}/BSB/{book_id}/{chapter_no}.simple.json")
    if chapter is None: raise RuntimeError(f"Missing BSB chapter {book_id} {chapter_no}")
    interlinear=fetch_json(f"{BASE}/d/bsb-interlinear/{book_id}/{chapter_no}.json")
    if interlinear is None: return book,chapter_no,verse_items(chapter),{}
    return book,chapter_no,verse_items(chapter),interlinear_verse_items(interlinear)

def write_jsonl(path, rows):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open("w",encoding="utf-8",newline="\n") as f:
        for row in rows: f.write(json.dumps(row,ensure_ascii=False,separators=(",",":"))+"\n")

def sha256(path):
    digest=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""): digest.update(chunk)
    return digest.hexdigest()

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--output-dir",type=Path,required=True)
    p.add_argument("--workers",type=int,default=6)
    a=p.parse_args()
    if not 1<=a.workers<=16: p.error("--workers must be between 1 and 16")
    out=a.output_dir; out.mkdir(parents=True,exist_ok=True)
    books_data=fetch_json(f"{BASE}/BSB/books.json")
    books=books_data["books"]
    actual_books=[(book["id"],int(book["numberOfChapters"])) for book in books]
    if actual_books!=EXPECTED_BOOKS:
        raise RuntimeError("BSB book order or chapter counts do not match the validated Protestant 66-book canon.")
    book_rows=[]
    for book in books:
        book_rows.append({
            "bookId":book["id"],"canonicalNumber":int(book["order"]),
            "name":book.get("name") or book.get("commonName") or book["id"],
            "testament":"OT" if int(book["order"])<=39 else "NT",
            "chapterCount":int(book["numberOfChapters"])
        })
    scripture_path=out/"scripture.ndjson"
    alignment_path=out/"alignments.ndjson"
    verse_count=0; alignment_count=0; chapters_without_interlinear=0
    eligible_words=0; matched_words=0; seen_alignments=set()
    chapter_jobs=[(book,n) for book in books for n in range(1,int(book["numberOfChapters"])+1)]
    results={}
    with ThreadPoolExecutor(max_workers=a.workers) as pool:
        futures=[pool.submit(process_chapter,book,n) for book,n in chapter_jobs]
        for future in as_completed(futures):
            book,n,texts,word_map=future.result()
            results[(int(book["order"]),n)]=(book,n,texts,word_map)
    with scripture_path.open("w",encoding="utf-8",newline="\n") as scripture, alignment_path.open("w",encoding="utf-8",newline="\n") as alignments:
        for key in sorted(results):
            book,n,texts,word_map=results[key]
            if not word_map: chapters_without_interlinear+=1
            for verse_no,text in sorted(texts.items()):
                scripture.write(json.dumps({
                    "translationId":"BSB","bookId":book["id"],
                    "bookName":book.get("name") or book.get("commonName") or book["id"],
                    "chapter":n,"verse":verse_no,"text":text
                },ensure_ascii=False,separators=(",",":"))+"\\n")
                verse_count+=1
                words=word_map.get(verse_no,[]) if isinstance(word_map,dict) else []
                rows,eligible,matched=alignment_rows(book["id"],n,verse_no,text,words)
                eligible_words+=eligible; matched_words+=matched
                for row in rows:
                    identity=(row["bookId"],row["chapter"],row["verse"],row["tokenIndex"],row["surfaceForm"],row["strongNumber"])
                    if identity in seen_alignments: continue
                    seen_alignments.add(identity)
                    alignments.write(json.dumps(row,ensure_ascii=False,separators=(",",":"))+"\n")
                    alignment_count+=1
    books_path=out/"books.ndjson"; write_jsonl(books_path,book_rows)
    expected_verses=int(books_data["translation"]["totalNumberOfVerses"])
    if verse_count!=expected_verses:
        raise RuntimeError(f"BSB verse count mismatch: expected {expected_verses}, built {verse_count}")
    if chapters_without_interlinear>max(1,int(len(chapter_jobs)*0.05)):
        raise RuntimeError(f"Too many chapters lack interlinear data: {chapters_without_interlinear}/{len(chapter_jobs)}")
    if eligible_words==0 or matched_words/eligible_words<0.85:
        raise RuntimeError(f"Interlinear gloss matching below 85%: {matched_words}/{eligible_words}")
    if alignment_count<100_000:
        raise RuntimeError(f"Unexpectedly low word alignment count: {alignment_count}")
    scripture_sha=sha256(scripture_path)
    manifest={
        "packId":"bsb-en-protestant-1","version":1,"translationId":"BSB",
        "translationName":"Berean Standard Bible","languageTag":"en","languageName":"English",
        "abbreviation":"BSB","canonId":"protestant-66","sourceId":"berean-bible",
        "licenseId":"public-domain",
        "copyrightNotice":"The Berean Standard Bible text is dedicated to the public domain.",
        "coverage":"COMPLETE_BIBLE","bookCount":len(book_rows),"verseCount":verse_count,
        "sha256":scripture_sha,"minimumReaderVersion":1,
        "attribution":"The Holy Bible, Berean Standard Bible (BSB). Text dedicated to the public domain.",
        "packFileName":"nearer-word-bsb-v1.zip"
    }
    manifest_path=out/"manifest.json"
    manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    bible_zip=out/"nearer-word-bsb-v1.zip"
    with zipfile.ZipFile(bible_zip,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        z.write(manifest_path,"manifest.json"); z.write(books_path,"books.ndjson"); z.write(scripture_path,"scripture.ndjson")
    print(f"BSB pack: {len(book_rows)} books, {verse_count} verses, SHA-256 {scripture_sha}, {bible_zip.stat().st_size} bytes")
    print(f"BSB word mappings: {alignment_count} token-to-Strong links; glosses matched {matched_words}/{eligible_words} words; {chapters_without_interlinear} chapters lacked interlinear data.")
    print(f"Alignment JSONL: {alignment_path} ({alignment_path.stat().st_size} bytes)")
if __name__=="__main__": main()
