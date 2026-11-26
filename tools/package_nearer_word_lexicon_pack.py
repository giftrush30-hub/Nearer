#!/usr/bin/env python3
"""Package source-attributed lexicon entries and/or verse-token alignments."""
from __future__ import annotations
import argparse, hashlib, json, zipfile
from pathlib import Path

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def count(path):
    with path.open("r",encoding="utf-8") as f:
        return sum(1 for line in f if line.strip())

def package(entries:Path,alignments:Path,output:Path,source_name:str,source_url:str,license_id:str,attribution:str):
    if not source_name.strip() or not source_url.startswith("https://") or not license_id.strip() or not attribution.strip():
        raise ValueError("Source name, HTTPS source URL, licence and attribution are required.")
    if not entries.exists() or not alignments.exists():
        raise ValueError("Both entries and alignments JSONL files must exist.")
    manifest={
        "sourceName":source_name,"sourceUrl":source_url,"licenseId":license_id,
        "attribution":attribution,"entryCount":count(entries),"alignmentCount":count(alignments),
        "entriesSha256":sha(entries),"alignmentsSha256":sha(alignments)
    }
    output.parent.mkdir(parents=True,exist_ok=True)
    manifest_file=output.with_suffix(".manifest.json")
    manifest_file.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    with zipfile.ZipFile(output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        z.write(manifest_file,"manifest.json")
        z.write(entries,"entries.ndjson")
        z.write(alignments,"alignments.ndjson")
    return manifest

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--entries",type=Path,required=True)
    p.add_argument("--alignments",type=Path,required=True)
    p.add_argument("--output",type=Path,required=True)
    p.add_argument("--source-name",required=True)
    p.add_argument("--source-url",required=True)
    p.add_argument("--license-id",required=True)
    p.add_argument("--attribution",required=True)
    a=p.parse_args()
    m=package(a.entries,a.alignments,a.output,a.source_name,a.source_url,a.license_id,a.attribution)
    print(f"Created {a.output}: {m['entryCount']} entries, {m['alignmentCount']} alignments")
if __name__=="__main__": main()
