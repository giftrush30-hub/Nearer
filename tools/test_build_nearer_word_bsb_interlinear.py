import unittest
from build_nearer_word_bsb_interlinear import canonical_strong, verse_items, alignment_rows, interlinear_verse_items

class BsbInterlinearBuilderTests(unittest.TestCase):
    def test_normalizes_zero_padded_strongs_and_sense_suffix(self):
        self.assertEqual(canonical_strong("H0430"),"H430")
        self.assertEqual(canonical_strong("G0026"),"G26")
        self.assertEqual(canonical_strong("H1254a"),"H1254")
        self.assertIsNone(canonical_strong(None))
        self.assertIsNone(canonical_strong("x123"))

    def test_exact_gloss_phrase_maps_tokens_and_multiple_strongs(self):
        text="In the beginning, God created"
        words=[
            {"bsbPos":1,"strongs":"H07225","gloss":"In the beginning"},
            {"bsbPos":1,"strongs":"G0001","gloss":"In the beginning"},
            {"bsbPos":3,"strongs":"H0430","gloss":"God"},
            {"bsbPos":4,"strongs":"H1254","gloss":"created"}
        ]
        rows,eligible,matched=alignment_rows("GEN",1,1,text,words)
        self.assertEqual(eligible,4)
        self.assertEqual(matched,4)
        beginning=[r for r in rows if r["surfaceForm"] in {"In","the","beginning"}]
        self.assertEqual({r["tokenIndex"] for r in beginning},{0,1,2})
        self.assertEqual({r["strongNumber"] for r in beginning},{"H7225","G1"})
        self.assertIn(("created",4,"H1254"),{(r["surfaceForm"],r["tokenIndex"],r["strongNumber"]) for r in rows})

    def test_one_interlinear_word_can_contain_multiple_strong_numbers(self):
        rows,eligible,matched=alignment_rows("GEN",1,1,"God created",[{"bsbPos":1,"strongs":["H0430","G2316"],"gloss":"God"}])
        self.assertEqual((eligible,matched),(1,1))
        self.assertEqual({r["strongNumber"] for r in rows},{"H430","G2316"})
        self.assertEqual({r["surfaceForm"] for r in rows},{"God"})

    def test_extracts_verse_items_from_simple_chapter(self):
        chapter={"chapter":{"content":[
            {"type":"heading","text":"Creation"},
            {"type":"verse","number":1,"text":"In the beginning"},
            {"type":"verse","number":2,"text":"The earth was formless"}
        ]}}
        self.assertEqual(verse_items(chapter),{1:"In the beginning",2:"The earth was formless"})

    def test_extracts_interlinear_words_by_verse(self):
        data={"chapter":{"content":[{"verse":1,"words":[{"bsbPos":1,"gloss":"In"}]},{"verse":2,"words":[]}]}}
        self.assertEqual(interlinear_verse_items(data),{1:[{"bsbPos":1,"gloss":"In"}],2:[]})

if __name__=="__main__": unittest.main()
