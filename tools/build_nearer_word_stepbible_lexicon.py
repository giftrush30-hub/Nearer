#!/usr/bin/env python3
"""Convert STEPBible TBESG/TBESH TSV lexicons into NeaRer JSONL entries.

This creates lexical entries only. It deliberately does not fabricate verse
alignments: TBESG/TBESH do not prove that an English translation token maps to
a particular original-language lemma.
"""
from __future__ import annotations
import argparse, csv, html, json, re
from html.parser import HTMLParser
from pathlib import Path

ATTRIBUTION = ("Lexical data from STEPBible-Data, created for STEPBible.org "
               "based on work at Tyndale House Cambridge and others; CC BY 4.0. "
               "Source: https://github.com/STEPBible/STEPBible-Data")
ALIASES = {
    "strong": ("estrong", "strongnumber", "strongsnumber", "strongnumberid", "strong"),
    "lemma": ("greek", "hebrew", "lemma", "headword", "word"),
    "transliteration": ("transliteration", "translit", "romanisation", "romanization"),
    "pos": ("partofspeech", "pos", "grammar", "morphology"),
    "gloss": ("gloss", "shortdefinition", "briefdefinition", "meaning"),
    "definition": ("extendeddefinition", "definition", "fullmeaning", "meaning"),
    "pronunciation": ("pronunciation", "phonetic"),
    "language": ("languagecode", "language", "lang"),
}
class TextOnly(HTMLParser):
    def __init__(self):
        super().__init__(); self.parts=[]
    def handle_data(self, data): self.parts.append(data)
    def text(self, value):
        self.parts=[]; self.feed(value or "")
        return re.sub(r"\s+", " ", html.unescape(" ".join(self.parts))).strip()

def norm(value): return re.sub(r"[^a-z0-9]", "", value.lower())

def choose(headers, aliases, required=True):
    normalized=[norm(h) for h in headers]
    for alias in aliases:
        if alias in normalized: return normalized.index(alias)
    for i,h in enumerate(normalized):
        if any(alias in h for alias in aliases): return i
    if required: raise ValueError("Missing required TSV column; expected one of: "+", ".join(aliases))
    return None

def convert(source:Path, output:Path, language:str):
    if language not in ("grc","hbo"): raise ValueError("language must be grc or hbo")
    parser=TextOnly(); records=[]; seen=set()
    with source.open("r",encoding="utf-8-sig",newline="") as f:
        rows=csv.reader(f,delimiter="\t",quotechar='"')
        indexes=None
        for row in rows:
            if not row or not any(cell.strip() for cell in row): continue
            if row[0].lstrip().startswith(("#","//","$")): continue
            if indexes is None:
                try:
                    strong_i=choose(row,ALIASES["strong"])
                    lemma_i=choose(row,ALIASES["lemma"])
                except ValueError:
                    continue
                gloss_i=choose(row,ALIASES["gloss"],required=False)
                definition_i=choose(row,ALIASES["definition"],required=False)
                if gloss_i is None and definition_i is None: continue
                indexes={"strong":strong_i,"lemma":lemma_i,"gloss":gloss_i,
                         "definition":definition_i,
                         "transliteration":choose(row,ALIASES["transliteration"],required=False),
                         "pos":choose(row,ALIASES["pos"],required=False),
                         "pronunciation":choose(row,ALIASES["pronunciation"],required=False),
                         "language":choose(row,ALIASES["language"],required=False)}
                continue
            def cell(key):
                i=indexes[key]
                return row[i].strip() if i is not None and i<len(row) else ""
            raw_strong=cell("strong").upper()
            strong_match=re.fullmatch(r"([GH])0*(\d+)[A-Z]?",raw_strong)
            strong=(strong_match.group(1)+str(int(strong_match.group(2)))) if strong_match else raw_strong
            lemma=parser.text(cell("lemma"))
            transliteration=parser.text(cell("transliteration")) or lemma
            gloss=parser.text(cell("gloss")) or parser.text(cell("definition"))
            definition=parser.text(cell("definition")) or gloss
            if not strong or not lemma or not gloss: continue
            expected="G" if language=="grc" else "H"
            if not strong.startswith(expected): continue
            if strong in ("G","H"): continue
            raw_lang=cell("language").strip().lower()
            entry_language=("arc" if raw_lang in ("arc","aramaic","biblical aramaic") else language)
            identity=(strong,lemma,transliteration,gloss,definition,entry_language)
            if identity in seen: continue
            seen.add(identity)
            stable=re.sub(r"[^A-Za-z0-9_-]","-",strong+"-"+lemma)[:100]
            records.append({
                "id":f"step-{entry_language}-{stable}-{len(records)+1}",
                "languageCode":entry_language,"lemma":lemma,"transliteration":transliteration,
                "pronunciation":parser.text(cell("pronunciation")) or transliteration,
                "strongNumber":strong,"partOfSpeech":parser.text(cell("pos")),
                "gloss":gloss,"definition":definition
            })
    if not records: raise ValueError("No lexical rows were converted; verify this source file's header/format.")
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open("w",encoding="utf-8",newline="\n") as f:
        for item in records: f.write(json.dumps(item,ensure_ascii=False)+"\n")
    return len(records)

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--greek",type=Path)
    p.add_argument("--hebrew",type=Path)
    p.add_argument("--output",type=Path,required=True)
    a=p.parse_args()
    total=0
    for path,lang in ((a.greek,"grc"),(a.hebrew,"hbo")):
        if path: total+=convert(path,a.output.with_name(a.output.stem+"-"+lang+a.output.suffix),lang)
    if not total: p.error("Supply --greek and/or --hebrew")
    print(f"Converted {total} lexical entries. No verse alignments were generated.")
if __name__=="__main__": main()
