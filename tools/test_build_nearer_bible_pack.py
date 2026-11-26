import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_nearer_bible_pack_from_usfm.py")
SPEC = importlib.util.spec_from_file_location("nearer_bible_pack_builder", MODULE_PATH)
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


class UsfmParserTests(unittest.TestCase):
    def test_parses_chapters_verses_and_inline_formatting(self):
        source = (
            "\\id GEN World English Bible\n"
            "\\c 1\n"
            "\\p\n"
            "\\v 1 In the \\add beginning\\add* God created the heavens and the earth.\n"
            "\\v 2 The earth was formless and empty. \\f + \\ft A note.\\f*\n"
            "\\c 2\n"
            "\\v 1 The heavens and the earth were finished.\n"
        )
        code, verses = BUILDER.parse_usfm(source, "GEN")
        self.assertEqual(code, "GEN")
        self.assertEqual(verses[(1, 1)], "In the beginning God created the heavens and the earth.")
        self.assertNotIn("A note", verses[(1, 2)])
        self.assertEqual(verses[(2, 1)], "The heavens and the earth were finished.")

    def test_preserves_punctuation_around_inline_usfm_markers(self):
        source = r"In the \add beginning\add*, God \w made|lemma=G123\w* the earth."
        self.assertEqual(
            BUILDER.clean_inline(source),
            "In the beginning, God made the earth."
        )

    def test_merges_subverse_letters_without_duplicate_verse_numbers(self):
        source = "\\id GEN\n\\c 1\n\\v 1a First part\n\\v 1b second part\n"
        _, verses = BUILDER.parse_usfm(source, "GEN")
        self.assertEqual(verses[(1, 1)], "First part second part")

    def test_rejects_verse_bridges_for_manual_versification_review(self):
        source = "\\id GEN\n\\c 1\n\\v 1-2 bridged text\n"
        with self.assertRaisesRegex(ValueError, "manual versification review"):
            BUILDER.parse_usfm(source, "GEN")

    def test_builder_book_order_contains_protestant_66(self):
        self.assertEqual(len(BUILDER.BOOKS), 66)
        self.assertEqual(BUILDER.BOOK_MAP["GEN"][0], 1)
        self.assertEqual(BUILDER.BOOK_MAP["REV"][0], 66)
        self.assertEqual(BUILDER.EXPECTED_CHAPTERS["PSA"], 150)
        self.assertEqual(BUILDER.EXPECTED_CHAPTERS["REV"], 22)
        self.assertEqual(len(BUILDER.EXPECTED_CHAPTERS), 66)


if __name__ == "__main__":
    unittest.main()
