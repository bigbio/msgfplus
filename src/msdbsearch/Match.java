package msdbsearch;

import java.util.Comparator;

import msgf.ScoreDist;

public class Match implements Comparable<Match> {
	private final int 		score;
	private final float		peptideMass;
	private final int		nominalPeptideMass;
	private final int		charge;
	private final String	pepSeq;
	
	// optional
	private int			deNovoScore;	
	private double		specProb = 1;
	private ScoreDist	scoreDist;
	
	public Match(int score, float peptideMass, int nominalPeptideMass, int charge, String pepSeq) {
		this.score = score;
		this.peptideMass = peptideMass;
		this.nominalPeptideMass = nominalPeptideMass;
		this.charge = charge;
		this.pepSeq = pepSeq;
	}
	
	public int getScore() {
		return score;
	}
	
	public float getPeptideMass()
	{
		return peptideMass;
	}
	
	public int getNominalPeptideMass()
	{
		return nominalPeptideMass;
	}
	
	public int getCharge() {
		return charge;
	}
	
	public String getPepSeq()
	{
		return pepSeq;
	}
	
	public void setDeNovoScore(int deNovoScore)
	{
		this.deNovoScore = deNovoScore;
	}
	
	public int getDeNovoScore()	{
		return deNovoScore;
	}

	public void setSpecProb(double specProb)
	{
		this.specProb = specProb;
	}
	
	public double getSpecProb() {
		return specProb;
	}
	
	public void setScoreDist(ScoreDist scoreDist)
	{
		this.scoreDist = scoreDist;
	}
	
	public ScoreDist getScoreDist()
	{
		return scoreDist;
	}
	
	@Override
	public int compareTo(Match o) {
		return score - o.score;
	}
	
	public static class SpecProbComparator implements Comparator<Match>
	{
		@Override
		public int compare(Match arg0, Match arg1) {
			if(arg0.getSpecProb() < arg1.getSpecProb())
				return 1;
			else if(arg0.getSpecProb() > arg1.getSpecProb())
				return -1;
			else
				return 0;
		}
	}
}