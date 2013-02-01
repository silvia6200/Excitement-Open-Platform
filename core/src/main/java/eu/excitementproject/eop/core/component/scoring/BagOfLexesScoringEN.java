package eu.excitementproject.eop.core.component.scoring;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Logger;

import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;

import eu.excitementproject.eop.common.component.lexicalknowledge.LexicalResourceCloseException;
import eu.excitementproject.eop.common.component.lexicalknowledge.LexicalResourceException;
import eu.excitementproject.eop.common.component.lexicalknowledge.LexicalRule;
import eu.excitementproject.eop.common.component.lexicalknowledge.RuleInfo;
import eu.excitementproject.eop.common.component.scoring.ScoringComponentException;
import eu.excitementproject.eop.core.component.lexicalknowledge.verb_ocean.RelationType;
import eu.excitementproject.eop.core.component.lexicalknowledge.verb_ocean.VerbOceanLexicalResource;
import eu.excitementproject.eop.core.component.lexicalknowledge.wordnet.WordnetLexicalResource;
import eu.excitementproject.eop.core.utilities.dictionary.wordnet.WordNetRelation;

/**
 * The <code>BagOfLexesScoring</code> class extends <code>BagOfLemmasScoring</code>.
 * It supports (currently) <code>WordNet</code> and <code>VerbOcean</code> two lexical resources.
 * 
 * @author  Rui
 */
public class BagOfLexesScoringEN extends BagOfLemmasScoring {
	
	static Logger logger = Logger.getLogger(BagOfLexesScoringEN.class.getName());
	
	private static final String WN_PATH = "./src/main/resources/EnglishWordNet-dict/";
	private static final String VO_PATH = "./src/main/resources/VerbOcean/verbocean.unrefined.2004-05-20.txt";

//	the number of features
	protected int numOfFeats = 0;
	
	@Override
	public int getNumOfFeats() {
		return numOfFeats;
	}
	
	private Set<WordnetLexicalResource> wnlrSet;
	
	private Set<WordNetRelation> wnlrRelSet;	
	public Set<WordNetRelation> getWnlrRelSet() {
		return wnlrRelSet;
	}

	private Set<VerbOceanLexicalResource> volrSet;
	
	private Set<RelationType> volrRelSet;
	public Set<RelationType> getVolrRelSet() {
		return volrRelSet;
	}
	
	public BagOfLexesScoringEN(Set<WordNetRelation> wnlrRelSet, Set<RelationType> volrRelSet) throws LexicalResourceException {
		this.wnlrRelSet = wnlrRelSet;
		this.volrRelSet = volrRelSet;
		if (null != wnlrRelSet && wnlrRelSet.size() != 0) {
			wnlrSet = new HashSet<WordnetLexicalResource>();
			for (WordNetRelation wnr : wnlrRelSet) {
				WordnetLexicalResource wnlr = new WordnetLexicalResource(new File(WN_PATH), true,true, Collections.singleton(wnr));
				wnlrSet.add(wnlr);
				numOfFeats ++;
			}
		}
		if (null != volrRelSet && volrRelSet.size() != 0) {
			volrSet = new HashSet<VerbOceanLexicalResource>();
			for (RelationType vor : volrRelSet) {
				VerbOceanLexicalResource volr = new VerbOceanLexicalResource(1, new File(VO_PATH), Collections.singleton(vor));
				volrSet.add(volr);
				numOfFeats ++;
			}
		}
	}
	
	@Override
	public String getComponentName() {
		return "BagOfLexesScoringEN";
	}
	
	public void close() throws ScoringComponentException {
		try {
			if (null != wnlrSet) {
				for (WordnetLexicalResource wnlr : wnlrSet) {
					wnlr.close();
				}
			}
			if (null != volrSet) {
				for (VerbOceanLexicalResource volr : volrSet) {
					volr.close();
				}
			}
		} catch (LexicalResourceCloseException e) {
			throw new ScoringComponentException(e.getMessage());
		}
	}
	
	@Override
	public Vector<Double> calculateScores(JCas aCas)
			throws ScoringComponentException {
		// 1) how many words of H (extended with multiple relations) can be found in T divided by the length of H
		Vector<Double> scoresVector = new Vector<Double>();
			
		try {
			JCas tView = aCas.getView("TextView");
			HashMap<String, Integer> tBag = countTokens(tView);

			JCas hView = aCas.getView("HypothesisView");
			HashMap<String, Integer> hBag = countTokens(hView);
			
			if (null != wnlrSet && wnlrSet.size() != 0) {
				for (WordnetLexicalResource wnlr : wnlrSet) {
					scoresVector.add(calculateSingleLexScoreWithWNRelations(tBag, hBag, wnlr));
				}
			}
			if (null != volrSet && volrSet.size() != 0) {
				for (VerbOceanLexicalResource volr : volrSet) {
					scoresVector.add(calculateSingleLexScoreWithVORelations(tBag, hBag, volr));
				}
			}
		} catch (CASException e) {
			throw new ScoringComponentException(e.getMessage());
		}
		return scoresVector;
	}
	
	protected double calculateSingleLexScoreWithWNRelations(HashMap<String, Integer> tBag, HashMap<String, Integer> hBag, WordnetLexicalResource wnlr) throws ScoringComponentException {
		double score = 0.0d;
		HashMap<String, Integer> tWordBag = new HashMap<String, Integer>();
		
		for (String word : tBag.keySet()) {
			int counts = tBag.get(word);
			try {
				tWordBag.put(word, counts);
				for (LexicalRule<? extends RuleInfo> rule : wnlr.getRulesForLeft(word, null)) {
					if (tWordBag.containsKey(rule.getRLemma())) {
						int tmp = tWordBag.get(rule.getRLemma());
						tWordBag.put(rule.getRLemma(), tmp + counts);
					} else {
						tWordBag.put(rule.getRLemma(), counts);
					}
				}
			} catch (LexicalResourceException e) {
				throw new ScoringComponentException(e.getMessage());
			}
		}
		
		score = calculateSimilarity(tWordBag, hBag).get(0);
		
		return score;
	}
	
	protected double calculateSingleLexScoreWithVORelations(HashMap<String, Integer> tBag, HashMap<String, Integer> hBag, VerbOceanLexicalResource volr) throws ScoringComponentException {
		double score = 0.0d;
		HashMap<String, Integer> tWordBag = new HashMap<String, Integer>();
		
		for (String word : tBag.keySet()) {
			int counts = tBag.get(word);
			try {
				tWordBag.put(word, counts);
				for (LexicalRule<? extends RuleInfo> rule : volr.getRulesForLeft(word, null)) {
					if (tWordBag.containsKey(rule.getRLemma())) {
						int tmp = tWordBag.get(rule.getRLemma());
						tWordBag.put(rule.getRLemma(), tmp + counts);
					} else {
						tWordBag.put(rule.getRLemma(), counts);
					}
				}
			} catch (LexicalResourceException e) {
				throw new ScoringComponentException(e.getMessage());
			}
		}
		
		score = calculateSimilarity(tWordBag, hBag).get(0);
		
		return score;
	}
}