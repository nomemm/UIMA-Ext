/**
 *
 */
package ru.kfu.itis.issst.uima.postagger;

import static ru.kfu.itis.cll.uima.cas.AnnotationUtils.toPrettyString;
import static ru.kfu.itis.cll.uima.util.DocumentUtils.getDocumentUri;

import java.util.*;

import com.google.common.base.Splitter;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.opencorpora.cas.Word;
import org.opencorpora.cas.Wordform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.uima.fit.util.JCasUtil;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import ru.kfu.cll.uima.tokenizer.fstype.Token;
import ru.kfu.itis.cll.uima.cas.AnnotationUtils;
import ru.kfu.itis.cll.uima.cas.FSUtils;
import ru.kfu.itis.cll.uima.util.DocumentUtils;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.GramModel;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.MorphDictionaryUtils;
import ru.kfu.itis.issst.uima.tokenizer.TokenUtils;

/**
 * @author Rinat Gareev (Kazan Federal University)
 */
public class MorphCasUtils {

    private static final Logger log = LoggerFactory.getLogger(MorphCasUtils.class);

    public static void addGrammeme(JCas jCas, Wordform wf, String newGram) {
        addGrammemes(jCas, wf, ImmutableList.of(newGram));
    }

    public static void addGrammemes(JCas jCas, Wordform wf, Iterable<String> newGrams) {
        LinkedHashSet<String> wfGrams = Sets.newLinkedHashSet(FSUtils.toSet(wf.getGrammems()));
        boolean changed = false;
        for (String newGram : newGrams) {
            changed |= wfGrams.add(newGram);
        }
        if (changed) {
            wf.setGrammems(FSUtils.toStringArray(jCas, wfGrams));
        }
    }

    public static void applyGrammems(Set<String> grams, Wordform wf) {
        if (grams == null || grams.isEmpty()) {
            return;
        }
        try {
            wf.setGrammems(FSUtils.toStringArray(wf.getCAS().getJCas(), grams));
        } catch (CASException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param word
     * @return the first wordform in the given Word annotation, or null if there
     * is no any wordform.
     */
    public static Wordform getOnlyWordform(Word word) {
        FSArray wfs = word.getWordforms();
        if (wfs == null || wfs.size() == 0) {
            return null;
        }
        if (wfs.size() > 1) {
            log.warn("Too much wordforms for Word {} in {}",
                    AnnotationUtils.toPrettyString(word),
                    DocumentUtils.getDocumentUri(word.getCAS()));
        }
        return (Wordform) wfs.get(0);
    }

    /**
     * @param word
     * @return the first wordform in the given Word annotation, never null
     */
    public static Wordform requireOnlyWordform(Word word) {
        Wordform wf = getOnlyWordform(word);
        if (wf == null) {
            throw new IllegalStateException(String.format(
                    "No wordforms in Word %s in %s",
                    toPrettyString(word), getDocumentUri(word.getCAS())));
        }
        return wf;
    }

    public static Set<String> getGrammemes(Word word) {
        Wordform wf = getOnlyWordform(word);
        if (wf == null) {
            return null;
        } else {
            return FSUtils.toSet(wf.getGrammems());
        }
    }

    public static Map<Token, Word> getToken2WordIndex(JCas jCas) {
        Map<Token, Word> result = Maps.newHashMap();
        for (Word word : JCasUtil.select(jCas, Word.class)) {
            Token token = (Token) word.getToken();
            if (token == null) {
                throw new IllegalStateException(String.format(
                        "No token assigned for Word %s in %s",
                        toPrettyString(word), getDocumentUri(jCas)));
            }
            if (result.put(token, word) != null) {
                throw new IllegalStateException(String.format(
                        "Shared token for Word %s in %s",
                        toPrettyString(word), getDocumentUri(jCas)));
            }
        }
        return result;
    }

    public static Map<Token, Word> getToken2WordIndex(JCas jCas, AnnotationFS span) {
        Map<Token, Word> result = Maps.newHashMap();
        for (Word word : JCasUtil.selectCovered(jCas, Word.class, span)) {
            Token token = (Token) word.getToken();
            if (token == null) {
                throw new IllegalStateException(String.format(
                        "No token assigned for Word %s in %s",
                        toPrettyString(word), getDocumentUri(jCas)));
            }
            if (result.put(token, word) != null) {
                throw new IllegalStateException(String.format(
                        "Shared token for Word %s in %s",
                        toPrettyString(word), getDocumentUri(jCas)));
            }
        }
        return result;
    }

    public static BitSet toGramBitSet(GramModel gm, org.opencorpora.cas.Wordform casWf) {
        return MorphDictionaryUtils.toGramBits(gm, FSUtils.toList(casWf.getGrammems()));
    }

    public static org.opencorpora.cas.Wordform addCasWordform(JCas jCas, Annotation tokenAnno) {
        Word word = new Word(jCas);
        word.setBegin(tokenAnno.getBegin());
        word.setEnd(tokenAnno.getEnd());
        word.setToken(tokenAnno);
        org.opencorpora.cas.Wordform casWf = new org.opencorpora.cas.Wordform(jCas);
        casWf.setWord(word);
        word.setWordforms(FSUtils.toFSArray(jCas, casWf));
        //
        word.addToIndexes();
        //
        return casWf;
    }

    /**
     * @param word annotation
     * @return a lemma of the first wordform in the word
     * @throws NullPointerException     if word is null
     * @throws IllegalArgumentException if there is no wordform in the word
     */
    public static String getFirstLemma(Word word) {
        if (word == null) {
            throw new NullPointerException("word");
        }
        FSArray wfs = word.getWordforms();
        if (wfs == null || wfs.size() == 0) {
            throw new IllegalArgumentException(String.format(
                    "No wordforms in %s", toPrettyString(word)));
        }
        return ((Wordform) wfs.get(0)).getLemma();
    }

    /**
     * @param word annotation
     * @return a PoS-tag of the first wordform in the word
     * @throws NullPointerException     if word is null
     * @throws IllegalArgumentException if there is no wordform in the word
     */
    public static String getFirstPosTag(Word word) {
        if (word == null) {
            throw new NullPointerException("word");
        }
        FSArray wfs = word.getWordforms();
        if (wfs == null || wfs.size() == 0) {
            throw new IllegalArgumentException(String.format(
                    "No wordforms in %s", toPrettyString(word)));
        }
        return ((Wordform) wfs.get(0)).getPos();
    }

    /**
     * Wrapper for {@link #getFirstLemma(Word)}.
     */
    public static final Function<Word, String> LEMMA_FUNCTION = new Function<Word, String>() {
        @Override
        public String apply(Word w) {
            return getFirstLemma(w);
        }
    };

    public static final Function<Word, String> POS_TAG_FUNCTION = new Function<Word, String>() {
        @Override
        public String apply(Word w) {
            return getFirstPosTag(w);
        }
    };

    public static final Function<Word, Set<String>> GRAMMEMES_FUNCTION = new Function<Word, Set<String>>() {
        @Override
        public Set<String> apply(Word w) {
            // TODO:LOW align with the previous two functions
            return getGrammemes(w);
        }
    };

    public static void makeSimplyWords(JCas jCas, Iterable<Word> aWords) {
        for (Word srcWord : aWords) {
            SimplyWord resWord = new SimplyWord(jCas, srcWord.getBegin(), srcWord.getEnd());
            resWord.setToken(resWord.getToken());
            FSArray wfs = srcWord.getWordforms();
            if (wfs != null && wfs.size() > 0) {
                Wordform wf = (Wordform) wfs.get(0);
                resWord.setPosTag(wf.getPos());
                resWord.setGrammems(wf.getGrammems());
                resWord.setLemma(wf.getLemma());
                resWord.setLemmaId(wf.getLemmaId());
            }
            resWord.addToIndexes();
        }
    }

    public static void makeSimplyWords(JCas jCas) {
        makeSimplyWords(jCas, JCasUtil.select(jCas, Word.class));
    }

    /**
     * Make Word annotation for each given simple word where Word.grammemes feature are filled by splitting
     * SimpleWord#posTag value by the given separator char.
     * <p> This method is intended for a quick test data creation.</p>
     *
     * @param jCas        -
     * @param simpleWords -
     * @param grammemeSep separator of grammatical values in 'posTag' feature of a SimpleWord annotation.
     * @see ru.kfu.itis.cll.uima.io.axml.AXMLReader
     */
    public static void makeWordsAndTokens(JCas jCas, Iterable<SimplyWord> simpleWords, char grammemeSep) {
        Splitter gramSplitter = Splitter.on(grammemeSep).omitEmptyStrings();
        for (SimplyWord simpleWord : simpleWords) {
            // make token
            Token token = TokenUtils.makeToken(jCas, simpleWord.getCoveredText(),
                    simpleWord.getBegin(), simpleWord.getEnd());
            token.addToIndexes();
            // assign token to the source simple word
            simpleWord.setToken(token);
            // make word
            Word resWord = new Word(jCas, simpleWord.getBegin(), simpleWord.getEnd());
            resWord.setToken(token);
            Wordform wf = new Wordform(jCas);
            wf.setWord(resWord);
            String posTag = simpleWord.getPosTag();
            if (posTag != null) {
                wf.setPos(posTag);
                wf.setGrammems(FSUtils.toStringArray(jCas, gramSplitter.splitToList(posTag)));
            }
            wf.setLemma(simpleWord.getLemma());
            wf.setLemmaId(simpleWord.getLemmaId());
            resWord.setWordforms(FSUtils.toFSArray(jCas, wf));
            resWord.addToIndexes();
        }
    }

    private MorphCasUtils() {
    }

}