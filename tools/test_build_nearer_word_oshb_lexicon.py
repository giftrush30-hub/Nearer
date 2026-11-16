import tempfile, unittest, json
from pathlib import Path
from build_nearer_word_oshb_lexicon import convert

SAMPLE="""<?xml version="1.0"?>
<lexicon xmlns="http://openscriptures.github.com/morphhb/namespace">
 <entry id="H1"><w pos="verb" pron="baw-raw" xlit="bara" xml:lang="hbo">בָּרָא</w><meaning><def>to create; to shape</def></meaning></entry>
 <entry id="H2"><w pos="noun" pron="aw-b" xlit="ab" xml:lang="arc">אַב</w><meaning><def>father</def></meaning></entry>
 <entry id="H3"><w pos="noun" xlit="empty">רֵיק</w></entry>
</lexicon>"""

class OpenScripturesHebrewConverterTests(unittest.TestCase):
    def test_converts_hebrew_and_explicit_aramaic(self):
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/"source.xml"; out=Path(d)/"entries.jsonl"
            src.write_text(SAMPLE,encoding="utf-8")
            self.assertEqual(convert(src,out),2)
            rows=[json.loads(x) for x in out.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(rows[0]["languageCode"],"hbo")
            self.assertEqual(rows[0]["strongNumber"],"H1")
            self.assertEqual(rows[0]["lemma"],"בָּרָא")
            self.assertEqual(rows[0]["definition"],"to create; to shape")
            self.assertEqual(rows[1]["languageCode"],"arc")

    def test_rejects_wrong_root(self):
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/"bad.xml"; out=Path(d)/"entries.jsonl"
            src.write_text("<wrong/>",encoding="utf-8")
            with self.assertRaises(ValueError): convert(src,out)

if __name__=="__main__": unittest.main()
