import json, tempfile, unittest
from pathlib import Path
from build_nearer_word_stepbible_lexicon import convert

class StepBibleLexiconConverterTests(unittest.TestCase):
    def test_greek_header_and_html_definition(self):
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/"greek.tsv"; out=Path(d)/"entries.jsonl"
            src.write_text("EStrong#\tGreek\tTransliteration\tPartOfSpeech\tGloss\tMeaning\nG3056\tλόγος\tlogos\tnoun\tword\t<div>speech <b>or reason</b></div>\n",encoding="utf-8")
            self.assertEqual(convert(src,out,"grc"),1)
            item=json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(item["strongNumber"],"G3056")
            self.assertEqual(item["lemma"],"λόγος")
            self.assertEqual(item["definition"],"speech or reason")
            self.assertEqual(item["languageCode"],"grc")

    def test_hebrew_header_and_duplicate_deduplication(self):
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/"hebrew.tsv"; out=Path(d)/"entries.jsonl"
            line="H1254a\tבָּרָא\tbara\tverb\tcreate\tto create\n"
            src.write_text("StrongNumber\tHebrewWord\tTransliteration\tPartOfSpeech\tGloss\tExtendedDefinition\n"+line+line,encoding="utf-8")
            self.assertEqual(convert(src,out,"hbo"),1)
            item=json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(item["strongNumber"],"H1254")
            self.assertEqual(item["lemma"],"בָּרָא")

    def test_refuses_unrecognised_header(self):
        with tempfile.TemporaryDirectory() as d:
            src=Path(d)/"bad.tsv"; out=Path(d)/"entries.jsonl"
            src.write_text("one\ttwo\nA\tB\n",encoding="utf-8")
            with self.assertRaises(ValueError): convert(src,out,"grc")

if __name__=="__main__": unittest.main()
