package ru.kfu.itis.issst.uima.ml;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.SequenceClassifier;
import org.cleartk.ml.jar.JarClassifierBuilder;
import ru.kfu.itis.issst.cleartk.JarSequenceClassifierFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Factory methods for {@link AbstractTieredSequenceClassifier}.
 *
 * @author Rinat Gareev
 */
public class TieredSequenceClassifiers {

    public static List<String> detectTierIds(File modelBaseDir) throws IOException {
        Properties featExtractionCfg = TieredFeatureExtractors.parseConfig(modelBaseDir);
        return TieredFeatureExtractors.getTiers(featExtractionCfg);
    }

    public static <I extends AnnotationFS> TieredSequenceClassifier<I, String> fromModelBaseDir(File modelBaseDir)
            throws ResourceInitializationException {
        final TieredFeatureExtractor<I, String> lFeatureExtractor;
        final List<SequenceClassifier<String>> lClassifiers;
        final List<String> tiers;
        try {
            Properties featExtractionCfg = TieredFeatureExtractors.parseConfig(modelBaseDir);
            tiers = TieredFeatureExtractors.getTiers(featExtractionCfg);
            lFeatureExtractor = TieredFeatureExtractors.from(featExtractionCfg);
            lClassifiers = createUnderlyingClassifiers(modelBaseDir, tiers);
        } catch (Exception e) {
            throw new ResourceInitializationException(e);
        }
        return new AbstractTieredSequenceClassifier<I>() {
            // initializer
            {
                this.tierIds = ImmutableList.copyOf(tiers);
                this.classifiers = lClassifiers;
                this.featureExtractor = lFeatureExtractor;
                Preconditions.checkState(tierIds.size() == classifiers.size());
            }
        };
    }


    private static List<SequenceClassifier<String>> createUnderlyingClassifiers(
            File modelBaseDir, Iterable<String> tierIds)
            throws IOException {
        List<SequenceClassifier<String>> resultList = Lists.newArrayList();
        for (String tierId : tierIds) {
            File tierModelDir = new File(modelBaseDir, tierId);
            File tierModelJar = JarClassifierBuilder.getModelJarFile(tierModelDir);
            JarSequenceClassifierFactory<String> clFactory = new JarSequenceClassifierFactory<>();
            clFactory.setClassifierJarPath(tierModelJar.getPath());
            org.cleartk.ml.SequenceClassifier<String> cl = clFactory.createClassifier();
            resultList.add(cl);
        }
        return resultList;
    }

    private TieredSequenceClassifiers() {
    }
}
