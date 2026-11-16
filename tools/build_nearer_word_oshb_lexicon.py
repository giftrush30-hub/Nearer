#!/usr/bin/env python3
"""Convert the Open Scriptures HebrewStrong.xml lexicon into NeaRer JSONL."""
from __future__ import annotations
import argparse, html, json, re
from html.parser import HTMLParser
from pathlib import Path
import xml.etree.ElementTree as ET

NS="{http://openscriptures.github.com/morphhb/namespace}"
XML_LANG="{http://www.w3.org/XML/1998/namespace}lang"
ATTRIBUTION=("Hebrew lexicon data from the Open Scriptures Hebrew Bible Project, "
             "licensed CC BY 4.0. Credit: Open Scriptures Hebrew Bible Project. "
             "Source: https://github.com/openscriptures/HebrewLexicon")

def clean(value):
    return re.sub(r"\s+"," ",html.unescape(value or "")).strip()

def convert(source:Path, output:Path):
    root=ET.parse(source).getroot()
    if root.tag != NS+"lexicon":
        raise ValueError("Expected Open Scriptures HebrewStrong.xml <lexicon> root.")
    rows=[]; seen=set()
    for entry in root.findall(NS+"entry"):
        raw_strong=entry.get("id","").strip().upper()
        strong_match=re.fullmatch(r"H0*(\d+)[A-Z]?",raw_strong)
        strong=("H"+str(int(strong_match.group(1)))) if strong_match else raw_strong
        if not strong.startswith("H"): continue
        words=entry.findall(NS+"w")
        word=next((w for w in words if clean("".join(w.itertext()))),None)
        if word is None: continue
        lemma=clean("".join(word.itertext()))
        transliteration=clean(word.get("xlit","")) or lemma
        pronunciation=clean(word.get("pron","")) or transliteration
        pos=clean(word.get("pos",""))
        raw_language=word.get(XML_LANG,"hbo").lower()
        language="arc" if raw_language in ("arc","aramaic") else "hbo"
        definition_parts=[]
        for container in entry.findall(NS+"meaning"):
            for node in container.findall(".//"+NS+"def"):
                value=clean("".join(node.itertext()))
                if value: definition_parts.append(value)
            if not definition_parts:
                value=clean("".join(container.itertext()))
                if value: definition_parts.append(value)
        if not definition_parts:
            for container in entry.findall(NS+"source"):
                for node in container.findall(".//"+NS+"def"):
                    value=clean("".join(node.itertext()))
                    if value: definition_parts.append(value)
        definition=" ".join(dict.fromkeys(definition_parts))
        if not definition: continue
        identity=(strong,lemma,transliteration,definition,language)
        if identity in seen: continue
        seen.add(identity)
        rows.append({
            "id":f"oshb-{language}-{strong}",
            "languageCode":language,"lemma":lemma,"transliteration":transliteration,
            "pronunciation":pronunciation,"strongNumber":strong,
            "partOfSpeech":pos,"gloss":definition.split(";")[0][:180],
            "definition":definition
        })
    if not rows: raise ValueError("No Hebrew lexical entries found in source XML.")
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open("w",encoding="utf-8",newline="\n") as f:
        for row in rows: f.write(json.dumps(row,ensure_ascii=False)+"\n")
    return len(rows)

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=Path,required=True)
    p.add_argument("--output",type=Path,required=True)
    a=p.parse_args()
    print(f"Converted {convert(a.input,a.output)} Open Scriptures Hebrew lexicon entries.")
if __name__=="__main__": main()
