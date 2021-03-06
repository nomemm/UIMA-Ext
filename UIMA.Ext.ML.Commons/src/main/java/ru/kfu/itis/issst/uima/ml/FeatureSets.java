package ru.kfu.itis.issst.uima.ml;

import com.google.common.base.Function;
import org.cleartk.ml.Feature;

import java.util.List;

/**
 * Static utilities to work with FeatureSet.
 *
 * @author Rinat Gareev
 */
public class FeatureSets {
    public static final Function<FeatureSet, List<Feature>> LIST_FUNCTION = new Function<FeatureSet, List<Feature>>() {
        @Override
        public List<Feature> apply(FeatureSet input) {
            return input.toList();
        }
    };

    /**
     * A factory method.
     *
     * @return a new instance of FeatureSet.
     */
    public static FeatureSet empty() {
        return new MultimapBasedFeatureSet();
    }

    private FeatureSets() {
    }
}
