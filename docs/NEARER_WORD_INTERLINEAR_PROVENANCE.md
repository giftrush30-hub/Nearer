# NeaRer Word — BSB original-language alignment provenance

## Source and rights

The Free Use Bible API documents the Berean Standard Bible text as public domain and the Berean interlinear tables as public domain. Its dataset reference describes the interlinear data as Hebrew, Aramaic and Greek words in original-text order, including Strong's number, transliteration, grammar, and the English phrase used by the BSB. The API's interlinear documentation explains that each word's `bsbPos` is its 1-based position in BSB English order and that the `gloss` is the BSB English phrase for that word.

Official references:
- Dataset and interlinear schema: https://bible.helloao.org/docs/reference/datasets/
- Berean Bible licensing explanation: https://bible.helloao.org/docs/guide/a-biblical-model-for-licensing-the-bible.html
- API project repository: https://github.com/HelloAOLab/bible-api

## What the builder does

`tools/build_nearer_word_bsb_interlinear.py` downloads the BSB text and the BSB interlinear dataset. It validates the expected 66-book Protestant canon and exact chapter counts, validates the reported total verse count, and checks that a substantial share of interlinear English glosses can be found in the corresponding BSB verse.

For each verse, the builder:
1. sorts original-language words by the source's BSB English-order rank (`bsbPos`);
2. normalises the word's BSB English gloss and the verse's whitespace-separated tokens;
3. searches forward through the BSB verse for the next matching gloss phrase;
4. writes one alignment row for each matched English token and the source Strong's number(s);
5. preserves the exact English token index and surface form used by the installed BSB text.

This is a phrase-matching reconstruction from the publisher/API's own interlinear gloss and ordering fields. It is not a direct publisher-supplied English-token index. It must therefore remain labelled as a reconstructed alignment, and ambiguous or unmatched phrases must not be guessed or silently forced into a match.

## Important limitations

- A Strong's number is a lexical index, not a complete grammatical or semantic interpretation.
- A single original-language word can correspond to several English tokens; the builder may associate the Strong's number with each token in the source gloss phrase.
- Several original-language words may share one English gloss phrase. The builder preserves multiple entries for a token rather than arbitrarily selecting one.
- Some original-language words have no Strong's number and are omitted from Strong's-based lookup.
- The current builder does not attach a separate morphology code or original-script lemma to every English token. Those details belong to the interlinear source word and require an expanded alignment schema before they are displayed as token-specific facts.
- A lexical lookup should display its source, Strong's number and language, and should not present a Strong's gloss as the only possible contextual meaning.
- If a phrase cannot be matched confidently, it remains unaligned. Never infer a Hebrew/Greek/Aramaic lemma from the English surface form.

## Publication gate

The generated pack is a research/validation artifact until:
- source metadata and attribution are carried into the installed pack manifest;
- pack import is tested against the Android importer;
- alignment counts and representative difficult verses are manually reviewed;
- a real-device test verifies tap-to-word results against the installed BSB verse text.

The generated alignment artifact must remain separate from the base APK.
