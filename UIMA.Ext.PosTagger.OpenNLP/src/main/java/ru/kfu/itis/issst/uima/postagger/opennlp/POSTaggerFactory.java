/**
 * 
 */
package ru.kfu.itis.issst.uima.postagger.opennlp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.uima.UIMAFramework;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactProvider;
import opennlp.tools.util.model.ArtifactSerializer;
import ru.kfu.cll.uima.tokenizer.fstype.Token;
import ru.kfu.itis.cll.uima.util.ConfigPropertiesUtils;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.CachedDictionaryDeserializer;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.CachedDictionaryDeserializer.GetDictionaryResult;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.MorphDictionary;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class POSTaggerFactory extends BaseToolFactory {

	private static final String GRAM_CATEGORIES_MANIFEST_ENTRY_NAME = "gramCategories";
	private static final String FEATURE_EXTRACTORS_ENTRY_NAME = "feature.extractors";

	private FeatureExtractorsBasedContextGenerator contextGenerator;
	private ImmutableSet<String> gramCategories;

	/**
	 * This constructor is required for OpenNLP model deserialization
	 */
	public POSTaggerFactory() {
	}

	public POSTaggerFactory(FeatureExtractorsBasedContextGenerator contextGenerator,
			Collection<String> gramCategories) {
		this.contextGenerator = contextGenerator;
		this.gramCategories = ImmutableSet.copyOf(gramCategories);
	}

	@Override
	protected void init(ArtifactProvider artifactProvider) {
		super.init(artifactProvider);
		String gramCategoriesStr = artifactProvider
				.getManifestProperty(GRAM_CATEGORIES_MANIFEST_ENTRY_NAME);
		if (gramCategoriesStr != null) {
			gramCategories = ImmutableSet.copyOf(gramCatSplitter.split(gramCategoriesStr));
		}
	}

	public BeamSearchContextGenerator<Token> getContextGenerator() {
		if (contextGenerator == null && artifactProvider != null) {
			contextGenerator = artifactProvider.getArtifact(FEATURE_EXTRACTORS_ENTRY_NAME);
		}
		return contextGenerator;
	}

	public Set<String> getGramCategories() {
		return gramCategories;
	}

	@Override
	public Map<String, Object> createArtifactMap() {
		Map<String, Object> artMap = super.createArtifactMap();
		artMap.put(FEATURE_EXTRACTORS_ENTRY_NAME, contextGenerator);
		return artMap;
	}

	@Override
	public Map<String, String> createManifestEntries() {
		Map<String, String> manifest = super.createManifestEntries();
		if (gramCategories != null) {
			manifest.put(GRAM_CATEGORIES_MANIFEST_ENTRY_NAME, gramCatJoiner.join(gramCategories));
		}
		return manifest;
	}

	private static final Splitter gramCatSplitter = Splitter.on(',');
	private static final Joiner gramCatJoiner = Joiner.on(',');

	@Override
	public void validateArtifactMap() throws InvalidFormatException {
		/*
		Object tagDictEntry = artifactProvider.getArtifact(TAG_DICTIONARY_ENTRY_NAME);
		if (tagDictEntry != null) {
			if (!(tagDictEntry instanceof MorphDictionaryAdapter)) {
				throw new InvalidFormatException(String.format(
						"Unknown type of tag dictionary: %s", tagDictEntry.getClass()));
			}
			// TOD check dict compliance
		}
		*/
		Object featExtractorsEntry = artifactProvider.getArtifact(FEATURE_EXTRACTORS_ENTRY_NAME);
		if (featExtractorsEntry == null) {
			throw new InvalidFormatException("No featureExtractors in artifacts map");
		}
		if (!(featExtractorsEntry instanceof FeatureExtractorsBasedContextGenerator)) {
			throw new InvalidFormatException(String.format(
					"Unknown type of feature extractors aggregate: %s",
					featExtractorsEntry.getClass()));
		}
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
		Map<String, ArtifactSerializer> artSerMap = super.createArtifactSerializersMap();
		// artSerMap.put("tagdict", new MorphDictionarySerializer());
		artSerMap.put("extractors", new FeatureExtractorsSerializer());
		return artSerMap;
	}

	static class FeatureExtractorsSerializer implements
			ArtifactSerializer<FeatureExtractorsBasedContextGenerator> {

		// A serializer instance is hold in a BaseModel instance,
		// so this key will have the same life-time as this BaseModel
		private Object dictCacheKey;

		@Override
		public FeatureExtractorsBasedContextGenerator create(InputStream in) throws IOException,
				InvalidFormatException {
			if (dictCacheKey != null) {
				throw new UnsupportedOperationException();
			}
			Properties props = new Properties();
			props.load(in);
			MorphDictionary dict = null;
			if (ConfigPropertiesUtils.getStringProperty(props,
					DefaultFeatureExtractors.PROP_DICTIONARY_VERSION, false) != null) {
				// load dictionary
				// TODO refactor out
				URL serDictUrl = UIMAFramework.newDefaultResourceManager()
						.resolveRelativePath("dict.opcorpora.ser");
				GetDictionaryResult getDictResult;
				try {
					getDictResult = CachedDictionaryDeserializer.getInstance()
							.getDictionary(serDictUrl, serDictUrl.openStream());
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
				dictCacheKey = getDictResult.cacheKey;
				dict = getDictResult.dictionary;
			}

			return DefaultFeatureExtractors.from(props, dict);
		}

		@Override
		public void serialize(FeatureExtractorsBasedContextGenerator artifact, OutputStream out)
				throws IOException {
			if (!(artifact instanceof DefaultFeatureExtractors)) {
				throw new UnsupportedOperationException();
			}
			Properties props = new Properties();
			DefaultFeatureExtractors.to((DefaultFeatureExtractors) artifact, props);
			props.store(out, "");
		}

	}

	/*
	static class MorphDictionarySerializer implements ArtifactSerializer<MorphDictionaryAdapter> {

		private static final Joiner gramCatJoiner = Joiner.on(',');
		private static final Splitter gramCatSplitter = Splitter.on(',');

		@Override
		public MorphDictionaryAdapter create(InputStream in) throws IOException,
				InvalidFormatException {
			Properties props = new Properties();
			props.load(in);
			Set<String> gramCats = ImmutableSet.copyOf(gramCatSplitter.split(
					props.getProperty(MorphDictionaryAdapter.PARAM_GRAM_CATEGORIES)));
			return new MorphDictionaryAdapter(gramCats);
		}

		@Override
		public void serialize(MorphDictionaryAdapter mda, OutputStream out) throws IOException {
			Properties props = new Properties();
			props.setProperty(MorphDictionaryAdapter.PARAM_GRAM_CATEGORIES,
					gramCatJoiner.join(mda.getGramCategories()));
			props.store(out, null);
		}
	}
	*/
}
