package ru.kfu.itis.issst.corpus.statistics.dao.corpus;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.apache.uima.cas.CAS;
import org.xml.sax.SAXException;

public interface CorpusDAO {

	Set<URI> getDocuments() throws URISyntaxException;

	Set<String> getAnnotatorIds(URI docURI) throws IOException;

	void getDocumentCas(URI docURI, String annotatorId, CAS aCAS)
			throws IOException, SAXException;

	boolean hasDocument(URI docURI, String annotatorId);

	void persist(URI docUri, String annotatorId, CAS cas)
			throws IOException, SAXException;
}
