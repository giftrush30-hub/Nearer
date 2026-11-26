import tempfile, unittest, json, zipfile
from pathlib import Path
from package_nearer_word_lexicon_pack import package

class LexiconPackagerTests(unittest.TestCase):
    def test_creates_attributed_pack_with_hashes_and_counts(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); entries=root/"entries.ndjson"; align=root/"alignments.ndjson"; out=root/"pack.zip"
            entries.write_text('{"id":"x"}\n',encoding="utf-8")
            align.write_text("",encoding="utf-8")
            m=package(entries,align,out,"Example source","https://example.org/source","CC-BY-4.0","Credit the source.")
            self.assertEqual(m["entryCount"],1)
            self.assertEqual(m["alignmentCount"],0)
            with zipfile.ZipFile(out) as z:
                self.assertEqual(set(z.namelist()),{"manifest.json","entries.ndjson","alignments.ndjson"})
                self.assertEqual(json.loads(z.read("manifest.json"))["entriesSha256"],m["entriesSha256"])

    def test_requires_https_source(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); e=root/"e"; a=root/"a"; e.write_text(""); a.write_text("")
            with self.assertRaises(ValueError):
                package(e,a,root/"p.zip","Example","http://example.org","PD","Credit")

if __name__=="__main__": unittest.main()
