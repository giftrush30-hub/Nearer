#!/usr/bin/env python3
"""Validate a generated NeaRer Word pack before it is published."""

import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

from build_nearer_bible_pack_from_usfm import BOOKS, EXPECTED_CHAPTERS

def verify(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        required={"manifest.json","books.ndjson","scripture.ndjson"}
        if set(archive.namelist()) != required:
            raise ValueError("Pack must contain exactly manifest.json, books.ndjson and scripture.ndjson.")
        manifest=json.loads(archive.read("manifest.json"))
        books=[json.loads(line) for line in archive.read("books.ndjson").splitlines() if line.strip()]
        scripture_bytes=archive.read("scripture.ndjson")
        verses=[json.loads(line) for line in scripture_bytes.splitlines() if line.strip()]

    if manifest.get("canonId")!="protestant-66" or manifest.get("coverage")!="COMPLETE_BIBLE":
        raise ValueError("This verifier accepts only complete Protestant 66-book packs.")
    if manifest.get("bookCount")!=66 or len(books)!=66:
        raise ValueError("The pack does not contain exactly 66 books.")
    if manifest.get("verseCount")!=len(verses):
        raise ValueError("Manifest verse count does not match Scripture data.")
    if hashlib.sha256(scripture_bytes).hexdigest()!=manifest.get("sha256"):
        raise ValueError("Scripture SHA-256 does not match the manifest.")

    book_by_id={}
    for index,book in enumerate(books,1):
        code,name,testament=BOOKS[index-1]
        if book.get("canonicalNumber")!=index or book.get("bookId")!=code:
            raise ValueError(f"Unexpected canonical book at position {index}.")
        if book.get("chapterCount")!=EXPECTED_CHAPTERS[code]:
            raise ValueError(f"Wrong chapter count for {name}.")
        book_by_id[code]=book

    seen=set()
    chapters={code:set() for code in book_by_id}
    for verse in verses:
        translation=verse.get("translationId")
        book=verse.get("bookId")
        chapter=verse.get("chapter")
        number=verse.get("verse")
        text=verse.get("text","")
        if translation!=manifest.get("translationId") or book not in book_by_id:
            raise ValueError("Verse references a different translation or unknown book.")
        if not isinstance(chapter,int) or chapter not in range(1,book_by_id[book]["chapterCount"]+1):
            raise ValueError(f"Invalid chapter in {book}.")
        if not isinstance(number,int) or number not in range(1,301) or not text.strip():
            raise ValueError(f"Invalid verse number or empty text in {book} {chapter}.")
        key=(book,chapter,number)
        if key in seen:
            raise ValueError(f"Duplicate verse reference: {key}.")
        seen.add(key)
        chapters[book].add(chapter)
        if re.search(r"\s+[,.;:!?]",text):
            raise ValueError(f"Unexpected whitespace before punctuation in {book} {chapter}:{number}.")

    for code,chapter_set in chapters.items():
        expected=set(range(1,EXPECTED_CHAPTERS[code]+1))
        if chapter_set!=expected:
            raise ValueError(f"Missing chapter(s) in {code}.")

    return {
        "translation":manifest["translationName"],
        "books":len(books),
        "chapters":sum(len(value) for value in chapters.values()),
        "verses":len(verses),
        "sha256":manifest["sha256"]
    }

if __name__=="__main__":
    try:
        result=verify(Path(sys.argv[1]))
        print("NeaRer Bible pack verified: "+json.dumps(result,ensure_ascii=False))
    except Exception as error:
        print("Bible pack verification failed: "+str(error),file=sys.stderr)
        raise SystemExit(2)
