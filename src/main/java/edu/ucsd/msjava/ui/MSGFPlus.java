package edu.ucsd.msjava.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import java.util.concurrent.TimeUnit;

import edu.ucsd.msjava.fdr.ComputeFDR;
import edu.ucsd.msjava.misc.ProgressData;
import edu.ucsd.msjava.misc.ThreadPoolExecutorWithExceptions;
import edu.ucsd.msjava.msdbsearch.CompactFastaSequence;
import edu.ucsd.msjava.msdbsearch.CompactSuffixArray;
import edu.ucsd.msjava.msdbsearch.ConcurrentMSGFPlus;
import edu.ucsd.msjava.msdbsearch.DBScanner;
import edu.ucsd.msjava.msdbsearch.MSGFPlusMatch;
import edu.ucsd.msjava.msdbsearch.ReverseDB;
import edu.ucsd.msjava.msdbsearch.ScoredSpectraMap;
import edu.ucsd.msjava.msdbsearch.SearchParams;
import edu.ucsd.msjava.msgf.Tolerance;
import edu.ucsd.msjava.msscorer.NewScorerFactory.SpecDataType;
import edu.ucsd.msjava.msutil.ActivationMethod;
import edu.ucsd.msjava.msutil.AminoAcidSet;
import edu.ucsd.msjava.msutil.DBSearchIOFiles;
import edu.ucsd.msjava.msutil.Enzyme;
import edu.ucsd.msjava.msutil.InstrumentType;
import edu.ucsd.msjava.msutil.Protocol;
import edu.ucsd.msjava.msutil.SpecFileFormat;
import edu.ucsd.msjava.msutil.SpecKey;
import edu.ucsd.msjava.msutil.SpectraAccessor;
import edu.ucsd.msjava.mzid.MZIdentMLGen;
import edu.ucsd.msjava.mzml.MzMLAdapter;
import edu.ucsd.msjava.params.ParamManager;
import edu.ucsd.msjava.sequences.Constants;
import java.util.logging.Level;
import java.util.logging.Logger;


public class MSGFPlus {
	public static final String VERSION = "Release (v2016.08.31)";
	public static final String RELEASE_DATE = "8/31/2016";
	
	public static final String DECOY_DB_EXTENSION = ".revCat.fasta";
	public static final String DECOY_PROTEIN_PREFIX = "XXX";
	
	public static void main(String argv[])
	{
		long time = System.currentTimeMillis();

		ParamManager paramManager = new ParamManager("MS-GF+", MSGFPlus.VERSION, MSGFPlus.RELEASE_DATE, "java -Xmx3500M -jar MSGFPlus.jar");
		paramManager.addMSGFPlusParams();
		
		if(argv.length == 0)
		{
			paramManager.printUsageInfo();
			return;
		}
		
		MzMLAdapter.turnOffLogs();
		
		// Parse parameters
		String errMessage = paramManager.parseParams(argv); 
		if(errMessage != null)
		{
			System.err.println("[Error] " + errMessage);
			System.out.println();
			paramManager.printUsageInfo();
			return;
		}
		
		// Running MS-GF+
		paramManager.printToolInfo();
		String errorMessage = null;
		try {
			errorMessage = runMSGFPlus(paramManager);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(-1);
		}
		
		if(errorMessage != null)
		{
			System.err.println("[Error] " + errorMessage);
			System.out.println();
		}
		else
			System.out.format("MS-GF+ complete (total elapsed time: %.2f sec)\n", (System.currentTimeMillis()-time)/(float)1000);
	}
	
    public static String runMSGFPlus(ParamManager paramManager)
	{
    	SearchParams params = new SearchParams();
    	String errorMessage = params.parse(paramManager);
    	if(errorMessage != null)
    		return errorMessage;
    	else
    	{
    		List<DBSearchIOFiles> ioList = params.getDBSearchIOList();
    		boolean multFiles = false;
    		if(ioList.size() >= 2)
    		{
    			System.out.println("Processing " + ioList.size() + " spectra");
        		for(DBSearchIOFiles ioFiles : ioList)
        			System.out.println("\t" + ioFiles.getSpecFile().getName());
    			multFiles = true;
    		}
    		
    		
    		int ioIndex = -1;
    		for(DBSearchIOFiles ioFiles : ioList)
    		{
    			++ioIndex;
    			File specFile = ioFiles.getSpecFile();
    			SpecFileFormat specFormat = ioFiles.getSpecFileFormat();
    			File outputFile = ioFiles.getOutputFile();
    			
    			if(multFiles)
    			{
    				if(!outputFile.exists())
    				{
        				System.out.println("\nProcessing " + specFile.getPath());
        				System.out.println("Writing results to " + outputFile.getPath());
        				String errMsg = runMSGFPlus(ioIndex, specFormat, outputFile, params);
        				if(errMsg != null)
        					return errMsg;
    				}
    				else
    				{
        				System.out.println("\nIgnoring " + specFile.getPath());
        				System.out.println("Output file " + outputFile.getPath() + " exists.");
    				}
    			}
    			else
    			{
    				String errMsg = runMSGFPlus(ioIndex, specFormat, outputFile, params);
    				if(errMsg != null)
    					return errMsg;
    			}
    		}
    	}
    	return null;
	}
    
    private static String runMSGFPlus(int ioIndex, SpecFileFormat specFormat, File outputFile, SearchParams params)
    {
		long time = System.currentTimeMillis();
		
		// Check the outputFile is valid for writing
		File parent = outputFile.getParentFile();
		if(parent != null && !parent.exists())
		{
			return "Cannot create " + outputFile.getPath() + "!";
		}
		
		// DB file
		File databaseFile = params.getDatabaseFile();
		
		// PM tolerance
		Tolerance leftParentMassTolerance = params.getLeftParentMassTolerance();
		Tolerance rightParentMassTolerance = params.getRightParentMassTolerance();
		
		int minIsotopeError = params.getMinIsotopeError();	// inclusive
		int maxIsotopeError = params.getMaxIsotopeError();	// inclusive
		
		Enzyme enzyme = params.getEnzyme();
		
		ActivationMethod activationMethod = params.getActivationMethod();
		InstrumentType instType = params.getInstType();
		Protocol protocol = params.getProtocol();
		
		AminoAcidSet aaSet = params.getAASet();
		
		int startSpecIndex = params.getStartSpecIndex();
		int endSpecIndex = params.getEndSpecIndex();
		
		boolean useTDA = params.useTDA();
		
		int minCharge = params.getMinCharge();
		int maxCharge = params.getMaxCharge();
		
		int numThreads = params.getNumThreads();
		boolean doNotUseEdgeScore = params.doNotUseEdgeScore();
		
		int minNumPeaksPerSpectrum = params.getMinNumPeaksPerSpectrum();
		if(minNumPeaksPerSpectrum == -1)	// not specified
		{
			if(instType == InstrumentType.TOF)
				minNumPeaksPerSpectrum = Constants.MIN_NUM_PEAKS_PER_SPECTRUM_TOF;
			else
				minNumPeaksPerSpectrum = Constants.MIN_NUM_PEAKS_PER_SPECTRUM;
		}
		
		System.out.println("Loading database files...");
		
		File dbIndexDir = params.getDBIndexDir();
		if(dbIndexDir != null)
		{
			File newDBFile = new File(dbIndexDir.getPath()+File.separator+databaseFile.getName());
			if(!useTDA)
			{
				if(!newDBFile.exists())
				{
					System.out.println("Creating " + newDBFile.getPath() + ".");
					ReverseDB.copyDB(databaseFile.getPath(), newDBFile.getPath());
				}
			}
			databaseFile = newDBFile;
		}
		
		if(useTDA)
		{
			String dbFileName = databaseFile.getName();
			String concatDBFileName = dbFileName.substring(0, dbFileName.lastIndexOf('.'))+DECOY_DB_EXTENSION;
			File concatTargetDecoyDBFile = new File(databaseFile.getAbsoluteFile().getParent()+File.separator+concatDBFileName);
			if(!concatTargetDecoyDBFile.exists())
			{
				System.out.println("Creating " + concatTargetDecoyDBFile.getPath() + ".");
				if(ReverseDB.reverseDB(databaseFile.getPath(), concatTargetDecoyDBFile.getPath(), true, DECOY_PROTEIN_PREFIX) == false)
				{
					return "Cannot create a decoy database file!";
				}
			}
			databaseFile = concatTargetDecoyDBFile;
		}
		
		DBScanner.setAminoAcidProbabilities(databaseFile.getPath(), aaSet);
		aaSet.registerEnzyme(enzyme);
		
		CompactFastaSequence fastaSequence = new CompactFastaSequence(databaseFile.getPath());
		if(useTDA)
		{
			float ratioUniqueProteins = fastaSequence.getRatioUniqueProteins();
			if(ratioUniqueProteins < 0.5f)
			{
				System.err.println("Error while indexing: " + databaseFile.getName() + " (too many redundant proteins)");
				System.err.println("If the database contains forward and reverse proteins, run MS-GF+ (or BuildSA) again with \"-tda 0\"");
				System.exit(-1);
			}
			
			float fractionDecoyProteins = fastaSequence.getFractionDecoyProteins();
			if(fractionDecoyProteins < 0.4f || fractionDecoyProteins > 0.6f)
			{
				System.err.println("Error while reading: " + databaseFile.getName() + " (fraction of decoy proteins: "+ fractionDecoyProteins+ ")");
				System.err.println("Delete " + databaseFile.getName() + " and run MS-GF+ again.");
				System.exit(-1);
			}
		}
		
		CompactSuffixArray sa = new CompactSuffixArray(fastaSequence, params.getMaxPeptideLength());
		System.out.print("Loading database finished ");
		System.out.format("(elapsed time: %.2f sec)\n", (float)(System.currentTimeMillis()-time)/1000);
		
		System.out.println("Reading spectra...");
		
		File specFile = params.getDBSearchIOList().get(ioIndex).getSpecFile();
		
		SpectraAccessor specAcc = new SpectraAccessor(specFile, specFormat);

		if(specAcc.getSpecMap() == null || specAcc.getSpecItr() == null)
			return "Error while parsing spectrum file: " + specFile.getPath();
		
		// determine the number of spectra to be scanned together 
//		long maxMemory = Runtime.getRuntime().maxMemory() - sa.getSize() - 1<<28;
//		int avgPeptideMass = 2000;
//		int numBytesPerMass = 12;
		
//		int numSpecScannedTogether;
//		if(maxMemory > Integer.MAX_VALUE)
//			numSpecScannedTogether = Integer.MAX_VALUE;
//		else
//			numSpecScannedTogether = (int)((float)maxMemory/avgPeptideMass/numBytesPerMass);
		
		int numSpecScannedTogether = Integer.MAX_VALUE;
		
		ArrayList<SpecKey> specKeyList = SpecKey.getSpecKeyList(specAcc.getSpecItr(), 
				startSpecIndex, endSpecIndex, minCharge, maxCharge, activationMethod, minNumPeaksPerSpectrum);
		int specSize = specKeyList.size();
		if(specSize == 0)
			return specFile.getPath() + " does not have any valid spectra";
		
		System.out.print("Reading spectra finished ");
		System.out.format("(elapsed time: %.2f sec)\n", (float)(System.currentTimeMillis()-time)/1000);
		
		//numThreads = Math.min(numThreads, Math.round(Math.min(specSize, numSpecScannedTogether)/1000f));
		numThreads = Math.min(numThreads, Math.round(specSize/1000f));
		if(numThreads <= 0)
			numThreads = 1;
			
		System.out.println("Using " + numThreads + (numThreads == 1 ? " thread." : " threads."));
		
		// Print out parameters
		System.out.println("Search Parameters:");
		System.out.println(params.toString());
		
		SpecDataType specDataType = new SpecDataType(activationMethod, instType, enzyme, protocol);
		int fromIndexGlobal = 0;
		
		List<MSGFPlusMatch> resultList = Collections.synchronizedList(new ArrayList<MSGFPlusMatch>());
		
		while(true)
		{
			if(fromIndexGlobal >= specSize)
				break;
			int toIndexGlobal = Math.min(specSize, fromIndexGlobal+numSpecScannedTogether);
			while(toIndexGlobal < specSize)
			{
				SpecKey lastSpecKey = specKeyList.get(toIndexGlobal-1);
				SpecKey nextSpecKey = specKeyList.get(toIndexGlobal);
				
				if(lastSpecKey.getSpecIndex() == nextSpecKey.getSpecIndex())
					toIndexGlobal++;
				else
					break;
			}
			
			System.out.println("Spectrum " + fromIndexGlobal + "-" + (toIndexGlobal-1) + " (total: " + specSize + ")");
			
			// Thread pool
			ThreadPoolExecutorWithExceptions executor = ThreadPoolExecutorWithExceptions.newFixedThreadPool(numThreads);
			
            int numTasks = Math.min(Math.min(numThreads * 10, 64), Math.round(specSize/250f));
            if (numThreads <= 1) {
                numTasks = 1;
            }
            System.out.println("Splitting work into " + numTasks + " tasks.");
            
			// Partition specKeyList
			int size = toIndexGlobal - fromIndexGlobal;
			int residue = size % numTasks;
			
			int[] startIndex = new int[numTasks];
			int[] endIndex = new int[numTasks];
			
			int subListSize = size/numTasks;
			for(int i=0; i<numTasks; i++)
			{
				startIndex[i] =  i > 0 ? endIndex[i-1] : fromIndexGlobal;
				endIndex[i] = startIndex[i] + subListSize + (i < residue ? 1 : 0);

				subListSize = size/numTasks;
				while(endIndex[i] < specKeyList.size())
				{
					SpecKey lastSpecKey = specKeyList.get(endIndex[i]-1);
					SpecKey nextSpecKey = specKeyList.get(endIndex[i]);
					
					if(lastSpecKey.getSpecIndex() == nextSpecKey.getSpecIndex())
					{
						++endIndex[i];
						--subListSize;
					}
					else
						break;
				}
			}
			
            try {
                for(int i=0; i<numTasks; i++)
                {
                	ScoredSpectraMap specScanner = new ScoredSpectraMap(
    		    			specAcc,
    		    			Collections.synchronizedList(specKeyList.subList(startIndex[i], endIndex[i])),
    		    			leftParentMassTolerance,
    		    			rightParentMassTolerance,
    		    			minIsotopeError,
    		    			maxIsotopeError,
    		    			specDataType,
    		    			params.outputAdditionalFeatures(),
    		    			false
    		    			);
    		    	if(doNotUseEdgeScore)
    		    		specScanner.turnOffEdgeScoring();
                    
                    ConcurrentMSGFPlus.RunMSGFPlus msgfdbExecutor = new ConcurrentMSGFPlus.RunMSGFPlus(
    						specScanner,
    						sa,
    						params,
    						resultList,
                            i + 1
    						);
    				executor.execute(msgfdbExecutor);
    			}
                
                executor.shutdown();
                
                int outputLimitCounter = 0;
                while (executor.getActiveCount() > 0) {
                    if (executor.HasThrownData()) {
                        // One task threw an exception, so all of the results will be incomplete. Exit.
                        Throwable data = executor.getThrownData();
                        if (data instanceof OutOfMemoryError) {
                            throw (OutOfMemoryError)data;
                        }
                        else {
                            throw data;
                        }
                    }
                    
                    if (outputLimitCounter % 60 == 0) {
                        // Output every minute
                        executor.outputProgressReport();
                    }
                    outputLimitCounter++;
                        
                    try {
                        Thread.sleep(1000); // sleep for one second; don't busy wait.
                    } catch (InterruptedException ex) {
                        Logger.getLogger(MSGFPlus.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
                try {
    				executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    			} catch (InterruptedException e)
    			{
    				e.printStackTrace();
                    Logger.getLogger(MSGFPlus.class.getName()).log(Level.SEVERE, null, e);
    			}
                // Output completed progress report.
                executor.outputProgressReport();
    			//while(!executor.isTerminated()) {}	// wait until all threads terminate
            } catch (OutOfMemoryError ex) {
                ex.printStackTrace();
                Logger.getLogger(MSGFPlus.class.getName()).log(Level.SEVERE, null, ex);
                executor.shutdownNow();
                return "Task terminated; results incomplete. Please run again with a greater amount of memory, using \"-Xmx4G\", for example.";
            } catch (Exception ex) {
                ex.printStackTrace();
                Logger.getLogger(MSGFPlus.class.getName()).log(Level.SEVERE, null, ex);
                executor.shutdownNow();
                return "Task terminated; results incomplete. Please run again.";
            } catch (Throwable ex) {
                ex.printStackTrace();
                Logger.getLogger(MSGFPlus.class.getName()).log(Level.SEVERE, null, ex);
                executor.shutdownNow();
                return "Task terminated; results incomplete. Please run again.";
            }
            
			fromIndexGlobal += numSpecScannedTogether;
		}
		
    	time = System.currentTimeMillis();

    	if(params.useTDA())
    	{
    		// Compute Q-values
    		System.out.println("Computing q-values...");
    		ComputeFDR.addQValues(resultList, sa, false);
    		System.out.print("Computing q-values finished ");
    		System.out.format("(elapsed time: %.2f sec)\n", (float)(System.currentTimeMillis()-time)/1000);
    	}

		// sort by spectral E-values
    	time = System.currentTimeMillis();
    	
		System.out.println("Writing results...");
		Collections.sort(resultList);

		MZIdentMLGen mzidGen = new MZIdentMLGen(params, aaSet, sa, specAcc, ioIndex);
		mzidGen.addSpectrumIdentificationResults(resultList);
    	
        mzidGen.writeResults(outputFile);

		System.out.print("Writing results finished ");
		System.out.format("(elapsed time: %.2f sec)\n", (float)(System.currentTimeMillis()-time)/1000);
    	
    	return null;
	}	    
}
