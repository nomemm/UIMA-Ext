package ru.ksu.niimm.cll.uima.morph.ml;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import org.apache.uima.UimaContext;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.classifier.Feature;
import org.cleartk.classifier.feature.extractor.CleartkExtractor;
import org.cleartk.classifier.feature.extractor.CleartkExtractorException;
import org.cleartk.classifier.feature.extractor.simple.CombinedExtractor;
import org.cleartk.classifier.feature.extractor.simple.SimpleFeatureExtractor;
import org.uimafit.component.initialize.ExternalResourceInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.initializable.Initializable;
import ru.kfu.cll.uima.tokenizer.fstype.Token;
import ru.kfu.itis.issst.uima.ml.DictionaryPossibleTagFeatureExtractor;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.GramModel;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.MorphDictionary;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.MorphDictionaryHolder;

import java.util.List;
import java.util.Set;

import static ru.kfu.itis.issst.uima.ml.DefaultFeatureExtractors.contextTokenExtractors;
import static ru.kfu.itis.issst.uima.ml.DefaultFeatureExtractors.currentTokenExtractors;

/**
 * @author Rinat Gareev
 */
public class SimpleTieredSequenceClassifier extends TieredSequenceClassifier implements Initializable {

    // constants
    public static final String RESOURCE_MORPH_DICTIONARY = "morphDictionary";
    public static final String PARAM_LEFT_CONTEXT_SIZE = "leftContextSize";
    public static final String PARAM_RIGHT_CONTEXT_SIZE = "rightContextSize";

    @ExternalResource(key = RESOURCE_MORPH_DICTIONARY, mandatory = true)
    private MorphDictionaryHolder morphDictHolder;
    @ConfigurationParameter(name = PARAM_LEFT_CONTEXT_SIZE, defaultValue = "2")
    private Integer leftContextSize;
    @ConfigurationParameter(name = PARAM_RIGHT_CONTEXT_SIZE, defaultValue = "2")
    private Integer rightContextSize;
    // TODO
    private GramTiers gramTiers;
    private MorphDictionary morphDictionary;
    // aggregate fields
    // CFE ~ a Common Feature Extractor
    private SimpleFeatureExtractor tokenCFE;
    private CleartkExtractor contextCFE;
    private List<SimpleFeatureExtractor> dictFeatureExtractors;

    @Override
    public void initialize(UimaContext ctx) throws ResourceInitializationException {
        ExternalResourceInitializer.initialize(ctx, this);
        morphDictionary = morphDictHolder.getDictionary();
        // parse context definitions for feature extractors
        // TODO:LOW here should be a single feature extraction config like in the Stanford tagger
        if (leftContextSize < 0 || rightContextSize < 0) {
            throw new IllegalStateException("context size < 0");
        }
        if (leftContextSize == 0 && rightContextSize == 0) {
            throw new IllegalStateException("left & right context sizes == 0");
        }
        List<CleartkExtractor.Context> contexts = Lists.newArrayList();
        if (leftContextSize > 0) {
            contexts.add(new CleartkExtractor.Preceding(leftContextSize));
        }
        if (rightContextSize > 0) {
            contexts.add(new CleartkExtractor.Following(rightContextSize));
        }
        CleartkExtractor.Context[] contextsArr = contexts.toArray(new CleartkExtractor.Context[contexts.size()]);
        // instantiate feature extractors
        tokenCFE = new CombinedExtractor(currentTokenExtractors().toArray(FE_ARRAY));
        contextCFE = new CleartkExtractor(Token.class,
                new CombinedExtractor(contextTokenExtractors().toArray(FE_ARRAY)),
                contextsArr);
        //
        dictFeatureExtractors = Lists.newArrayList();
        for (int tier = 0; tier < gramTiers.getCount(); tier++) {
            Set<String> curTierCats = gramTiers.getTierCategories(tier);
            Set<String> prevTierCats = Sets.newHashSet();
            for (int prevStep = 0; prevStep < tier; prevStep++) {
                prevTierCats.addAll(gramTiers.getTierCategories(prevStep));
            }
            SimpleFeatureExtractor dfe = new DictionaryPossibleTagFeatureExtractor(
                    curTierCats, prevTierCats, morphDictionary);
            dictFeatureExtractors.add(dfe);
        }
    }

    @Override
    protected void onBeforeTier(List<FeatureSet> featSets, int tier,
                                JCas jCas, Annotation spanAnno, List<Token> tokens)
            throws CleartkExtractorException {
        SimpleFeatureExtractor dfe = dictFeatureExtractors.get(tier);
        for (int i = 0; i < featSets.size(); i++) {
            Token tok = tokens.get(i);
            FeatureSet tokFeatSet = featSets.get(i);
            tokFeatSet.add(dfe.extract(jCas, tok), dfe);
        }
    }

    // TODO define in a single place (probably, a kind of 'Output Label Generator' in a training data writer)
    // Note that this is different from the tier joiner, even though currently they use the same separator char
    private static final Splitter GRAM_SPLITTER = Splitter.on('&');

    @Override
    protected void onAfterTier(List<FeatureSet> featSets, List<String> tierOutLabels, int tier,
                               JCas jCas, Annotation spanAnno, List<Token> tokens) {
        // parse tier output labels into feature values
        List<Iterable<String>> parsedTierOutLabels = Lists.newArrayListWithExpectedSize(tierOutLabels.size());
        for (String tol : tierOutLabels) {
            if (Strings.isNullOrEmpty(tol)) parsedTierOutLabels.add(ImmutableSet.<String>of());
            else parsedTierOutLabels.add(GRAM_SPLITTER.split(tol));
        }
        //
        SimpleFeatureExtractor dfe = dictFeatureExtractors.get(tier);
        for (int tokPos = 0; tokPos < featSets.size(); tokPos++) {
            // remove tier-specific features
            FeatureSet tokFeatSet = featSets.get(tokPos);
            tokFeatSet.removeFeaturesBySource(dfe);
            // extract feature from a new data - the new label of this tier
            List<Feature> gramFeatures = Lists.newArrayListWithExpectedSize(leftContextSize + rightContextSize + 1);
            int left = Math.max(0, tokPos - leftContextSize);
            int right = Math.min(tokens.size() - 1, tokPos + rightContextSize);
            for (int contextTokPos = left; contextTokPos <= right; contextTokPos++) {
                // a context token relative position
                final int contextTokRelPos = tokPos - contextTokPos;
                for (String gram : parsedTierOutLabels.get(contextTokPos)) {
                    gramFeatures.add(new Feature("Gram_at_" + contextTokRelPos, gram));
                }
            }
            tokFeatSet.add(gramFeatures, mockGramExtractor);
        }
    }

    @Override
    protected List<FeatureSet> extractCommonFeatures(JCas jCas, Annotation spanAnno, List<Token> tokens)
            throws CleartkExtractorException {
        List<FeatureSet> resultList = Lists.newArrayListWithExpectedSize(tokens.size());
        for (Token tok : tokens) {
            FeatureSet fs = FeatureSets.empty();
            fs.add(tokenCFE.extract(jCas, tok), tokenCFE);
            fs.add(contextCFE.extractWithin(jCas, tok, spanAnno), contextCFE);
            resultList.add(fs);
        }
        return resultList;
    }

    private static final SimpleFeatureExtractor[] FE_ARRAY = new SimpleFeatureExtractor[0];

    private final SimpleFeatureExtractor mockGramExtractor = new SimpleFeatureExtractor() {
        @Override
        public List<Feature> extract(JCas view, Annotation focusAnnotation) throws CleartkExtractorException {
            // should never be called
            throw new UnsupportedOperationException();
        }
    };
}
