/**
 * 
 */
package ru.ksu.niimm.cll.uima.morph.baseline;

import static ru.ksu.niimm.cll.uima.morph.baseline.PUtils.addCasWordform;
import static ru.ksu.niimm.cll.uima.morph.baseline.PUtils.normalizeToDictionary;

import java.util.BitSet;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.util.JCasUtil;

import ru.kfu.cll.uima.tokenizer.fstype.Token;
import ru.kfu.itis.cll.uima.cas.FSUtils;
import ru.ksu.niimm.cll.uima.morph.opencorpora.AnnotationAdapter;
import ru.ksu.niimm.cll.uima.morph.opencorpora.DefaultAnnotationAdapter;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class DictionaryAwareBaselineTagger extends DictionaryAwareBaselineAnnotator {

	public static final String PARAM_USE_DEBUG_GRAMMEMS = "useDebugGrammems";
	public static final String RESOURCE_WFSTORE = "WordformStore";

	// config fields
	@ExternalResource(key = RESOURCE_WFSTORE, mandatory = true)
	private WordformStore wfStore;
	@ConfigurationParameter(name = PARAM_USE_DEBUG_GRAMMEMS, defaultValue = "false")
	private boolean useDebugGrammems;
	private AnnotationAdapter wordAnnoAdapter;

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		wordAnnoAdapter = new DefaultAnnotationAdapter();
		wordAnnoAdapter.init(dict);
	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		for (Token token : JCasUtil.select(jCas, Token.class)) {
			if (!PUtils.canCarryWord(token)) {
				continue;
			}
			String tokenStr = token.getCoveredText();
			tokenStr = normalizeToDictionary(tokenStr);
			Set<BitSet> dictEntries = trimAndMergePosBits(dict.getEntries(tokenStr));
			if (dictEntries == null || dictEntries.isEmpty()) {
				if (useDebugGrammems) {
					setNotDictionary(jCas, addCasWordform(jCas, token));
				}
			} else if (dictEntries.size() == 1) {
				wordAnnoAdapter.apply(jCas, token, null, null, dictEntries.iterator().next());
			} else {
				BitSet posBits = wfStore.getPosBits(tokenStr);
				if (posBits != null) {
					wordAnnoAdapter.apply(jCas, token, null, null, posBits);
				} else {
					if (useDebugGrammems) {
						setAmbiguous(jCas, addCasWordform(jCas, token));
					}
				}
			}
		}
	}

	public static final String GRAMMEME_NOT_DICT = "not-dict";
	public static final String GRAMMEME_AMBIGUOUS = "ambiguous";

	private void setNotDictionary(JCas jCas, org.opencorpora.cas.Wordform casWf) {
		casWf.setGrammems(FSUtils.toStringArray(jCas, GRAMMEME_NOT_DICT));
	}

	private void setAmbiguous(JCas jCas, org.opencorpora.cas.Wordform casWf) {
		casWf.setGrammems(FSUtils.toStringArray(jCas, GRAMMEME_AMBIGUOUS));
	}
}