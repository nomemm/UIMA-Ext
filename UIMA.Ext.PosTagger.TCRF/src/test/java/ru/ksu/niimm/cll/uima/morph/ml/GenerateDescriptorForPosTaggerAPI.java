/**
 *
 */
package ru.ksu.niimm.cll.uima.morph.ml;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.xml.sax.SAXException;
import ru.kfu.itis.issst.uima.ml.TieredSequenceClassifierResource;
import ru.kfu.itis.issst.uima.postagger.PosTaggerAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Rinat Gareev (Kazan Federal University)
 */
public class GenerateDescriptorForPosTaggerAPI {

    public static void main(String[] args) throws IOException, UIMAException, SAXException {
        String outPath = "src/main/resources/"
                + PosTaggerAPI.AE_POSTAGGER.replace('.', '/')
                + ".xml";
        //
        AnalysisEngineDescription resultDesc = EmbeddedSeqClassifierBasedPosTagger.createDescription(
                TieredSequenceClassifierResource.RU_MODEL_BASE_PATH);
        resultDesc.getAnalysisEngineMetaData().setName("PoS-tagger-RU");
        // write to an XML file
        FileOutputStream out = FileUtils.openOutputStream(new File(outPath));
        try {
            // preserve imports = true
            resultDesc.toXML(out, true);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }
}
