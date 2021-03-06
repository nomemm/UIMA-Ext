package ru.kfu.itis.issst.uima.depparser.lab;

import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Properties;

import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.kfu.itis.cll.uima.io.IoUtils;
import ru.kfu.itis.cll.uima.util.Slf4jLoggerImpl;
import ru.kfu.itis.issst.uima.morph.dictionary.MorphDictionaryAPIFactory;
import ru.kfu.itis.issst.uima.postagger.PosTaggerAPI;
import ru.kfu.itis.issst.uima.segmentation.SentenceSplitterAPI;
import ru.kfu.itis.issst.uima.tokenizer.TokenizerAPI;

import com.beust.jcommander.Parameter;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import de.tudarmstadt.ukp.dkpro.lab.task.Dimension;

public abstract class LabLauncherBase {
	static {
		Slf4jLoggerImpl.forceUsingThisImplementation();
	}

	protected final Logger log = LoggerFactory.getLogger(getClass());

	@Parameter(names = "--parameters-file", required = false)
	protected File parametersFile = new File(getClass().getSimpleName() + ".parameters");

	// prepare input TypeSystem
	protected TypeSystemDescription inputTS = createTypeSystemDescription(
			"ru.kfu.itis.cll.uima.commons.Commons-TypeSystem",
			TokenizerAPI.TYPESYSTEM_TOKENIZER,
			SentenceSplitterAPI.TYPESYSTEM_SENTENCES,
			PosTaggerAPI.TYPESYSTEM_POSTAGGER);
	// prepare morph dictionary resource
	protected ExternalResourceDescription morphDictDesc = MorphDictionaryAPIFactory
			.getMorphDictionaryAPI()
			.getResourceDescriptionForCachedInstance();
	//
	private Properties parameterProps;

	private List<String> getParamVals(String paramName) throws IOException {
		if (parameterProps == null) {
			parameterProps = IoUtils.readProperties(parametersFile);
		}
		String str = parameterProps.getProperty(paramName);
		if (str == null) {
			return Lists.newArrayList((String) null);
		}
		return Lists.newArrayList(paramValSplitter.split(str));
	}

	private static final Splitter paramValSplitter = Splitter.on(';').trimResults();

	protected Dimension<File> getFileDimension(String paramName) throws IOException {
		return getDimension(paramName, File.class, str2File);
	}

	protected Dimension<Integer> getIntDimension(String paramName) throws IOException {
		return getDimension(paramName, Integer.class, str2Int);
	}

	protected Dimension<Boolean> getBoolDimension(String paramName) throws IOException {
		return getDimension(paramName, Boolean.class, str2Boolean);
	}

	protected Dimension<String> getStringDimension(String paramName) throws IOException {
		return getDimension(paramName, String.class, Functions.<String> identity());
	}

	private <V> Dimension<V> getDimension(String paramName, Class<V> valClass,
			Function<String, V> converter) throws IOException {
		return toDimension(paramName, Lists.transform(getParamVals(paramName), converter), valClass);
	}

	@SuppressWarnings("unchecked")
	private static <V> Dimension<V> toDimension(String name, List<V> list, Class<V> valClass) {
		return Dimension.create(name, list.toArray(
				(V[]) Array.newInstance(valClass, list.size())));
	}

	private static final Function<String, File> str2File = new Function<String, File>() {
		@Override
		public File apply(String input) {
			if (input == null) {
				return null;
			}
			if ("null".equalsIgnoreCase(input)) {
				return null;
			}
			return new File(input);
		}
	};

	private static final Function<String, Integer> str2Int = new Function<String, Integer>() {
		@Override
		public Integer apply(String input) {
			if (input == null) {
				return null;
			}
			return Integer.valueOf(input);
		}
	};

	private static final Function<String, Boolean> str2Boolean = new Function<String, Boolean>() {
		@Override
		public Boolean apply(String input) {
			if (input == null) {
				return null;
			}
			if ("0".equals(input)) {
				return false;
			}
			if ("1".equals(input)) {
				return true;
			}
			return Boolean.valueOf(input);
		}
	};
}
