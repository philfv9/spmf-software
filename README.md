[![License](https://img.shields.io/github/license/philfv9/spmf-software.svg)](https://github.com/philfv9/spmf-software/blob/main/LICENSE)
[![Release](https://img.shields.io/github/v/release/philfv9/spmf-software.svg)](https://github.com/philfv9/spmf-software/releases/latest)
[![Stars](https://img.shields.io/github/stars/philfv9/spmf-software.svg)](https://github.com/philfv9/spmf-software/stargazers)
[![Made with Java](https://img.shields.io/badge/Made%20with-Java-yellow)]()
[![SPMF](https://img.shields.io/badge/SPMF-300%2B%20Algorithms-blue)](http://www.philippe-fournier-viger.com/spmf/)

<div align="center">
  <h1>The SPMF Open-Source Pattern Mining Sofware</h1>
  <img src="images/spmf.png" alt="SPMF Logo" width="200">
</div>

**[SPMF](http://philippe-fournier-viger.com/spmf/)** is a popular and highly efficient **data mining software** written in **Java**, specialized in **pattern mining**. It provides over **300 algorithms** for various tasks such as:

- Frequent itemset mining
- Association rule mining
- Sequential pattern mining
- Sequential rule mining
- High-utility itemset mining
- Episode mining
- Graph mining
- Time series analysis
- and more

SPMF offers a graphical user interface (GUI), a command-line interface (CLI), and a server for alternatively running data mining algorithms through REST queries from a Python or Web client. SPMF can also be integrated in Python, R and other languages through wrappers and its CLI, or used as a Java library in Java projects.  SPMF is lightweight, actively developed and has **no external dependencies**.

The latest release is **SPMF version v2.65**, released on February 18, 2026.

The **official website of SPMF** with full documentation, tutorials, and other resources is:  [http://philippe-fournier-viger.com/spmf/](http://philippe-fournier-viger.com/spmf/)

---

## Table of Contents

- [Quickstart](#quickstart)
- [Documentation](#documentation)
- [Algorithms](#algorithms)
- [Datasets](#datasets)
- [Architecture](#architecture)
- [How to learn more?](#how-to-learn-more)
- [Contributing](#contributing)
- [License](#license)
- [How to Cite SPMF](#how-to-cite-spmf)
- [Authors](#authors)

---

## Quickstart

There are five ways to use SPMF, depending on your needs:

---

### 1 — Graphical User Interface (GUI)

<div align="center">
  <img src="/images/case1.png" alt="SPMF GUI use case" width="600">
</div>

The most simple way to use SPMF is through its integrated GUI.  To run the GUI:
1) Download the files `spmf.jar` and `test_files.zip` to your computer and make sure that Java is installed on your computer.
2) Uncompress the file `test_files.zip` on your desktop. It will create a folder containing some example data files that you can use with the algorithms.
3) Launch the GUI of SPMF by double-clicking on the file `spmf.jar`. If it does not work and you are using Windows, right-click on `spmf.jar` and select "open with..." and then select "Java Platform". If this option is not there, perhaps that Java is not installed on your computer, or that the PATH environment variable does not include your Java installation. 
4) If the previous step succeeds, the graphical interface of SPMF will open. 

<div align="center">
  <img src="/images/gui.png" alt="SPMF GUI" width="600">
</div>

5) Then, from the user interface, you can select input files, choose an algorithm from more than 300 algorithms, sets its parameters, and run the algorithms. For example, let's say that you want to run the **CM-SPAM** algorithm.   In the [documentation](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php), it is the [CM-SPAM example](https://philippe-fournier-viger.com/spmf/CM-SPAM.php). To run that example:
   
 - Click on the combo box beside **"Choose an algorithm"** and select **"CM-SPAM"**.  
 - Click on **"Choose input file"** and select `contextPrefixSpan.txt` from the `test_files` folder.  
 - Click on **"Choose output file"**, select a location (e.g., Desktop), enter `result.txt`, and click **"OK"**.
 - Set the parameter **minsup (%)** to `0.5` (as in the example).  
 - Other parameters are optional and can be ignored for this example.  
 - Click on **"Run algorithm"**.  
 - A new window will open showing the results.  
 - The results correspond to the patterns discovered by CM-SPAM (see the [CM-SPAM example](https://philippe-fournier-viger.com/spmf/CM-SPAM.php) of the documentation for interpretation).
That’s all. To run another algorithm, follow the same steps.

---

### 2 — Command Line

<div align="center">
  <img src="/images/case2.png" alt="SPMF terminal use case" width="600">
</div>

The second to use SPMF is through its command line interface (CLI) from the console.  To run the SPMF using the CLI:
1) Download the files `spmf.jar` and `test_files.zip` to your computer and make sure that Java is installed on your computer.
2) Uncompress the file `test_files.zip` on your desktop. It will create a folder containing some example data files that you can use with the algorithms.
3) To run an algorithm, go to the [documentation](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php) of SPMF  find the example corresponding to the algorithm that you want to run. For example, let's say that you want to run the **PrefixSpan** algorithm. It is this [example](https://philippe-fournier-viger.com/spmf/PrefixSpan.php) in the [documentation](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php).
5) Open the command prompt (if you are using Windows) or the terminal (if you are using Linux). Then, type the command specified in the example. For example, for PrefixSpan, the command is:
   
    ```java -jar spmf.jar run PrefixSpan contextPrefixSpan.txt output.txt 50%```
  
This command means to run the algorithm named "PrefixSpan" to use the input file named "contextPrefixSpan.txt" to set the output file for the results as "output.txt" to set the parameter of the algorithm (minsup) to 50 %. After executing this command, the file output.txt will be created. It will contain the result.

<div align="center">
  <img src="/images/cmd.png" alt="SPMF console" width="600">
</div>


The input and output file format of each algorithm is described in the  [documentation](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php).

That's all. If you want to run another algorithm, then follow the same steps.

---

### 3 — Java API

<div align="center">
  <img src="/images/case3.png" alt="SPMF API use case" width="600">
</div>

The third way to use SPMF is by integrating it into Java projects. For this, you can download `spmf.jar` and include it in the classpath of your Java project, and then call the classes from SPMF from your Java program. Or alternatively, you can download `spmf.zip` or clone the project to obtain SPMF source code. 
To get a good grasp of how the source code of SPMF is organized, you may read the [developers guide](http://philippe-fournier-viger.com/spmf/index.php?link=developers.php).

In general, each algorithm in SPMF is organized in its own package containing the main implementation files. Some utility classes are shared across multiple algorithms and are located in common directories such as `ca/pfv/spmf/input/` and `ca/pfv/spmf/patterns/`.  To how to run one algorithm from the source code, consider the SPAM algorithm. It is implemented in the package `ca/pfv/spmf/algorithms/sequentialpatterns/spam`, which is itself a subpackage of `ca/pfv/spmf/algorithms/sequentialpatterns/`, reflecting the fact that SPAM belongs to the family of sequential pattern mining algorithms. 

Each algorithm follows a consistent design pattern. There is a main class whose name starts with `Algo`, and this class provides a method called `runAlgorithm()` to execute the algorithm. For example, the SPAM algorithm is implemented in the class `AlgoSPAM.java`. Its `runAlgorithm()` method expects three parameters: the path to the input file, the path to the output file, and a minimum support threshold.
To understand how an algorithm is executed in practice, one can examine the example files located in the directory `ca/pfv/spmf/test/`. This directory contains demonstration code intended for developers. Each algorithm typically has at least one corresponding test file named `MainTestXXXX.java`, where `XXXX` is the name of the algorithm. 

In the case of SPAM, the relevant example is `MainTestSPAM_saveToFile.java`.

Example code of how to run the SPAM algorithm:

```java
// Load a sequence database
String input = fileToPath("contextPrefixSpan.txt");
String output = ".//output.txt";

// Create an instance of the algorithm
AlgoSPAM algo = new AlgoSPAM();

// Execute the algorithm with minsup = 2 sequences (50%)
algo.runAlgorithm(input, output, 0.5);
algo.printStatistics();
```

In this example, the input file `contextPrefixSpan.txt` corresponds to the dataset used in the official documentation and can be found in the `ca/pfv/spmf/tests/` directory. The output file is written to `.//output.txt`, although this path can be replaced by any valid location on the user’s system. The call to `runAlgorithm()` triggers the execution of the algorithm. The value `0.5` represents the minimum support threshold, whose exact interpretation is described in the SPAM documentation. Finally, each algorithm is associated with a description class located in the package `ca/pfv/spmf/algorithmmanager/descriptions`. These classes provide metadata such as the authors of the algorithm, the expected input and output formats, the list of parameters, and instructions for execution. They play an important role in the system: the graphical user interface relies on them to dynamically populate the list of available algorithms, while the command-line interface uses them to inform users about the required parameters and usage details. For instance, the SPAM algorithm is documented by the class `DescriptionAlgoSPAM`.
Other algorithms can be run in a similar way.

The input and output file format of each algorithm is described in the  [documentation](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php).

---

### 4 — Wrappers for Other Languages

<div align="center">
  <img src="/images/case4.png" alt="SPMF wrappers use case" width="600">
</div>

The fourth way to use SPMF is to call it from language developed using other programming languages using   wrappers for **Python, R, C#, etc.** via community-provided wrappers that invoke the command-line interface of SPMF.
A list of wrappers is [here](https://www.philippe-fournier-viger.com/spmf/index.php?link=spmfwrappers.php).

---

### 5 — REST API via SPMF-Server *(new)*

<div align="center">
  <img src="images/spmf-server.png" alt="SPMF-Server framework" width="600">
</div>

The fifth way to use SPMF is through the **[SPMF-Server](https://github.com/philfv9/spmf-server)**, a lightweight
HTTP server that wraps the SPMF library and exposes all algorithms as a **REST API**. This lets any language or tool submit mining jobs over HTTP and retrieve results without needing a local Java integration. This can be useful to run SPMF on a remote machine and query it from a client, from the browser or integrate it into a web application or microservice. 
Currently, the SPMF server can be used with the [SPMF Server Python CLI and GUI clients](https://github.com/philfv9/spmf-server-pythonclient) or  the [SPMF Server Web client](https://github.com/philfv9/spmf-server-webclient). For more details about how to install and run the SPMF-Server, please see the [SPMF-Server](https://github.com/philfv9/spmf-server) project.

## Documentation

The main documentation of SPMF and other resources can be found on the SPMF website. 
- **List of algorithms:**
  [https://philippe-fournier-viger.com/spmf/index.php?link=algorithms.php](https://philippe-fournier-viger.com/spmf/index.php?link=algorithms.php)
- **Main documentation** (with examples for each algorithm):
  [https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php)
- **Release notes and download**
  [download page](https://philippe-fournier-viger.com/spmf/index.php?link=download.php)
- **FAQ:**
  [https://philippe-fournier-viger.com/spmf/index.php?link=FAQ.php](https://philippe-fournier-viger.com/spmf/index.php?link=FAQ.php)


---

## Algorithms

SPMF offers more than 300 algorithms and tools. A brief overview of the algorithms is presented below. The full list can be found on the [algorithms](https://philippe-fournier-viger.com/spmf/index.php?link=algorithms.php) page of the website.

### Sequential Pattern Mining

| Category | Algorithms |
|----------|-----------|
| **Frequent Sequential Patterns** | CM-SPADE, CM-SPAM, FAST, GSP, LAPIN, PrefixSpan, SPADE, SPAM |
| **Closed Sequential Patterns** | ClaSP, CM-ClaSP, CloFAST, CloSpan, BIDE+ |
| **Maximal Sequential Patterns** | VMSP, MaxSP |
| **Top-k Sequential Patterns (support-based)** | TKS, TSP |
| **Top-k Sequential Patterns (leverage/significance)** | Skopus |
| **Generator Sequential Patterns** | VGEN, FEAT, FSGP |
| **Nonoverlapping Sequential Patterns** | NOSEP |
| **Compressing Sequential Patterns** | GoKrimp, SeqKrimp, HMG-GA, HMG-SA |
| **Quantile Cohesive Sequential Patterns** | QCSP |
| **Frequent Multidimensional Sequential Patterns** | SeqDIM |
| **Frequent Closed Multidimensional Sequential Patterns** | Songram et al. |
| **Multidimensional Sequential Patterns (combined features)** | Fournier-Viger et al. |
| **High-Utility Sequential Patterns** | USPAN |
| **High-Utility Probability Sequential Patterns** | PHUSPM, UHUSPM |
| **Cost-Efficient Sequential Patterns** | CorCEPB, CEPB, CEPN |
| **Progressive Sequential Pattern Mining** | ProSecCo |
| **Sequential Patterns with Flexible Constraints** | SPM-FC-L, SPM-FC-P, Occur |
| **Time Interval Related Patterns (TIRP)** | FastTIRP, VertTIRP |

### Sequential Rule Mining

| Category | Algorithms |
|----------|-----------|
| **Sequential Rules** | ERMiner, RuleGrowth, CMRules, CMDeo |
| **Sequential Rules (Zaki-based)** | RuleGen |
| **Sequential Rules with Window Size** | TRuleGrowth |
| **Top-k Sequential Rules** | TopSeqRules |
| **Top-k Class Sequential Rules** | TopSeqClassRules |
| **Top-k Non-redundant Sequential Rules** | TNS |
| **High-Utility Sequential Rules** | HUSRM |

### Sequence Prediction

| Algorithm | Reference |
|-----------|-----------|
| CPT+ | Gueniche et al., 2015 |
| CPT | Gueniche et al., 2013 |
| PPM (1st-order Markov) | Clearly et al., 1984 |
| Dependency Graph (DG) | Padmanabhan, 1996 |
| AKOM | Pitkow, 1999 |
| TDAG | Laird & Saul, 1994 |
| LZ78 | Ziv, 1978 |

### Itemset Mining

| Category | Algorithms |
|----------|-----------|
| **Frequent Itemsets** | Apriori, AprioriTID, FP-Growth, Eclat, dEclat, Relim, H-Mine, LCMFreq, PrePost, PrePost+, FIN, DFIN, NegFIN, DIC, TM, SAM, LinearTable |
| **Closed Frequent Itemsets** | FPClose, Charm, dCharm, DCI_Closed, LCM, AprioriClose, AprioriTID Close, NAFCP, NEclatClosed, CARPENTER, DBVMiner, NEWCHARM |
| **Recovering Frequent Itemsets from Closed** | LevelWise, DFI-Growth, DFI-List |
| **Maximal Frequent Itemsets** | FPMax, Charm-MFI, CARPENTER-MAX, GENMAX |
| **Frequent Itemsets with Multiple Min. Supports** | MSApriori, CFPGrowth++ |
| **Generator Itemsets** | DefMe, Talky-G, Talky-G-Diffset, Pascal, Zart |
| **Perfectly Rare Itemsets** | AprioriInverse, AprioriTIDInverse |
| **Minimal Rare Itemsets** | AprioriRare, AprioriTIDRare |
| **Rare Correlated Itemsets** | CORI, RP-Growth |
| **Targeted & Dynamic Queries on Itemsets** | Itemset-Tree, Memory-Efficient Itemset-Tree |
| **Recent Frequent Itemsets in Streams** | estDec, estDec+ |
| **Frequent Closed Itemsets in Streams** | CloStream |
| **Frequent Itemsets in Uncertain Data** | U-Apriori |
| **Erasable Itemsets** | VME |
| **Fuzzy Frequent Itemsets** | FFI-Miner, MFFI-Miner |
| **Self-sufficient Itemsets** | OPUS-Miner |
| **Compressing Itemsets (MDL)** | KRIMP, SLIM, GRIMP, HMP-SA, HMP-HC |
| **Top-k Frequent Itemsets** | Apriori(top-k), FPGrowth(top-k) |

### Episode Mining

| Category | Algorithms |
|----------|-----------|
| **Frequent Episodes (head frequency)** | EMMA, AFEM, MINEPI+ |
| **Frequent Episodes (minimal occurrences)** | MINEPI |
| **Top-k Frequent Episodes** | TKE |
| **Maximal Frequent Episodes** | MaxFEM |
| **Frequent Parallel Episodes (distinct occurrences)** | EMDO |
| **Partially-ordered Episode Rules (non-overlapping support)** | POERM |
| **All Partially-ordered Episode Rules** | POERM-ALL |
| **Partially-ordered Episode Rules (head support)** | POERMH |
| **Episode Rules (non-overlapping frequency)** | NONEPI |
| **Episode Rules from Parallel Episodes** | EMDO-Rules, EMDOP-Rules |
| **Episode Rules (generated from frequent episodes)** | Generator from TKE / AFEM / EMMA / MINEPI+ output |
| **High-Utility Episodes** | HUE-SPAN, US-SPAN |
| **Top-k High-Utility Episodes** | TUP |
| **Nonoverlapping Episodes** | NOSEP |
| **Episodes with Periodic Wildcard Gaps** | MAPD |
| **One-off Weak-gap Strong Episodes** | OWSP-Miner |

### Periodic Pattern Mining

| Category | Algorithms |
|----------|-----------|
| **Frequent Periodic Patterns (single sequence)** | PFPM |
| **Stable Periodic Itemsets** | SPP-Growth |
| **Top-k Stable Periodic Itemsets** | TSPIN |
| **Locally Periodic Patterns** | LPP-Growth, LPPM_breadth, LPPM_depth |
| **Non-redundant Periodic Patterns** | NPFPM |
| **Productive Periodic Patterns** | PPFP |
| **Self-reliant Periodic Patterns** | SRPFPM |
| **Periodic High-Utility Itemsets** | PHM, PHMN, PHMN+ |
| **Irregular (Non-periodic) High-Utility Itemsets** | PHM_irregular |
| **Periodic Patterns in Multiple Sequences** | MPFPS_BFS, MPFPS_DFS |
| **Rare Correlated Periodic Patterns (multiple sequences)** | MRCPPS |

### Graph Pattern Mining

| Category | Algorithms |
|----------|-----------|
| **All Frequent Subgraphs** | gSpan |
| **Top-k Frequent Subgraphs** | TKG |
| **Frequent Closed Subgraphs** | cgSpan |
| **Sequential Patterns in Dynamic Attributed Graphs** | TSeqMiner |
| **Association Rules in Dynamic Attributed Graphs** | AER-Miner |

### High-Utility Pattern Mining

| Category | Algorithms |
|----------|-----------|
| **High-Utility Itemsets (HUI)** | EFIM, FHM, HUI-Miner, HUP-Miner, mHUIMiner, UFH, HMiner, ULB-Miner, IHUP, Two-Phase, UP-Growth, UP-Growth+, UP-Hist, d2HUP, FHIM, PUCPMiner, RMiner |
| **HUI with Length Constraints** | FHM+ |
| **Correlated HUI (bond measure)** | FCHM_bond |
| **Correlated HUI (all-confidence measure)** | FCHM_allconfidence |
| **Correlated HUI (other)** | ECHUM |
| **HUI with Negative Utility** | FHN, HUINIV-Mine |
| **Multi-level HUI (with taxonomy)** | MLHUI-Miner |
| **Cross-level HUI (with taxonomy)** | CLH-Miner |
| **Multi-level & Cross-level HUI (combined)** | FEACP |
| **Low-cost HUI** | LCIM |
| **Frequent HUI** | FHMFreq |
| **On-shelf HUI (items with time periods)** | FOSHU, TS-HOUN |
| **Incremental HUI** | EIHI, HUI-LIST-INS |
| **Incremental Closed HUI** | IncCHUI |
| **Closed HUI** | EFIM-Closed, CHUI-Miner, CLS-Miner, HMiner_Closed, CHUD |
| **Maximal HUI** | CHUI-Miner(Max) |
| **Generator HUI** | HUG-Miner |
| **Generators of HUI** | GHUI-Miner |
| **Minimal HUI** | MinFHM |
| **Closed HUI and Generators** | HUCI_Miner |
| **Skyline HUI** | SkyMine |
| **Skyline Frequent HUI** | SFUI_UF, SFU_CE, SFUPMinerUemax, EMSFUI_D, EMSFUI_B |
| **Top-k HUI** | TKU, TKO-Basic, THUI |
| **Heuristic Top-k HUI** | TKU-CE, TKU-CE+ |
| **Top-k HUI from Streams** | FHMDS, FHMDS-Naive |
| **Quantitative HUI** | FHUQI-Miner, VHUQI |
| **Top-k Quantitative HUI** | TKQ |
| **Correlated Quantitative HUI** | CHUQI-Miner |
| **HUI via Evolutionary Algorithms** | HUIM-GA, HUIM-GA-tree, HUIF-GA |
| **HUI via Swarm Intelligence** | HUIM-ACO, HUIM-SPSO, HUIM-BPSO, HUIM-BPSO-tree, HUIF-PSO, HUIF-BA, HUIM-ABC |
| **HUI via Other Meta-heuristics** | HUIM-AF, HUIM-HC, HUIM-SA |
| **High Average-Utility Itemsets** | HAUI-Miner, EHAUPM, HAUIM-GMU |
| **High Average-Utility with Multiple Thresholds** | HAUI-MMAU, MEMU |
| **Top-k High Average-Utility Itemsets** | ETAUIM |
| **Local High-Utility Itemsets** | LHUI-Miner |
| **Peak High-Utility Itemsets** | PHUI-Miner |
| **Locally Trending High-Utility Itemsets** | LTHUI-Miner |
| **HUI with Recency Constraint** | ScentedUtilityMiner |
| **High-Utility Association Rules (all + non-redundant)** | HGB_all |
| **High-Utility Association Rules (non-redundant)** | HGB |

### Association Rule Mining

| Category | Algorithms |
|----------|-----------|
| **All Association Rules (confidence)** | Standard algorithm (Agrawal & Srikant, 1994) |
| **All Association Rules (lift)** | Adapted standard algorithm |
| **Informative & Generic Basis** | IGB (Gasmi et al., 2005) |
| **Sporadic Association Rules** | AprioriInverse-based (Koh & Roundtree, 2005) |
| **Closed Association Rules** | Closed rule algorithm (Szathmary et al., 2006) |
| **Minimal Non-redundant Association Rules** | Kryszkiewicz, 1998 |
| **Indirect Association Rules** | Indirect (Tan et al., 2000, 2006) |
| **Hiding Sensitive Association Rules** | FHSAR (Weng et al., 2008) |
| **Top-k Association Rules** | TopKRules, ETARM, FTARM |
| **Top-k Non-redundant Association Rules** | TNR |
| **Top-k Class Association Rules** | TopKClassRules |
| **Class Association Rules** | ACAC, ACCF, ACN, ADT, CBA, CBA2, CMAR, L3, MAC |
| **High-Utility Association Rules (all + non-redundant)** | HGB_all |
| **High-Utility Association Rules (non-redundant)** | HGB |

### Stream Pattern Mining

| Category | Algorithms |
|----------|-----------|
| **Recent Frequent Itemsets** | estDec, estDec+ |
| **Frequent Closed Itemsets** | CloStream |
| **Top-k High-Utility Itemsets** | FHMDS, FHMDS-Naive |

### Clustering

| Category | Algorithms |
|----------|-----------|
| **Partition-based** | K-Means, Bisecting K-Means, K-Means++ |
| **Density-based** | DBScan, OPTICS, Density Peak Clustering (DPC), AEDBScan |
| **Hierarchical** | Hierarchical Clustering |

### Time Series Mining

| Category | Methods |
|----------|---------|
| **Symbolic Representation** | SAX (converts time series to symbol sequences) |
| **Prior Moving Average** | Noise removal via prior moving average |
| **Cumulative Moving Average** | Noise removal via cumulative moving average |
| **Central Moving Average** | Noise removal via central moving average |
| **Median Smoothing** | Noise removal via median smoothing |
| **Exponential Smoothing** | Noise removal via exponential smoothing |
| **Normalization** | Min-max normalization |
| **Standardization** | Z-score standardization |
| **Differencing** | 1st and 2nd order differencing |
| **Data Reduction** | Piecewise Aggregate Approximation (PAA) |
| **Autocorrelation** | Autocorrelation function |
| **Linear Regression** | Least squares linear regression |
| **Segmentation** | Split by segment length, split into fixed number of segments |
| **Clustering** | K-Means, Bisecting K-Means, DBScan, OPTICS, Hierarchical (applied to time series) |

### Classification

| Category | Algorithms |
|----------|-----------|
| **Decision Trees** | ID3 |
| **Instance-based** | KNN (K-Nearest Neighbor) |
| **Class Association Rule-based** | ACAC, ACCF, ACN, ADT, CBA, CBA2, CMAR, L3, MAC |
| **Evaluation Framework** | Holdout & k-fold cross-validation |

### Text Mining

| Task | Method |
|------|--------|
| **Document Classification** | Naive Bayes classifier |
| **Document Clustering** | tf×idf-based clustering |

### Dataset Generation Tools

| Tool |
|------|
| Synthetic transaction database generator |
| Synthetic sequence database generator |
| Synthetic sequence database with timestamps generator |
| Clustering dataset generator |

### Dataset Transformation Tools

| Tool |
|------|
| Sequence database → transaction database |
| Transaction database → sequence database |
| Text file → sequence database (each sentence = one sequence) |
| Sequence database format converter (CSV, KOSARAK, BMS, IBM → SPMF) |
| Transaction database format converter (CSV → SPMF) |
| Time series → sequence database |
| Utility value generator for transaction databases |
| Timestamp adder for sequence databases |
| Transaction database fixer (with/without utility/time info) |
| Utility information remover |
| Database resizer (by percentage of lines) |
| Record sampler (reservoir, seed, etc.) |
| Duplicate record remover |

### Dataset Statistics Tools

| Tool |
|------|
| Transaction database |
| Transaction database with utility |
| Transaction database with cost and utility |
| Transaction database with utility and period info |
| Transaction database with utility and timestamps |
| Sequence database |
| Sequence database with cost and binary utility |
| Sequence database with cost and numeric utility |
| Sequence database with utility |
| Time-extended sequence database |
| Multi-dimensional sequence database |
| Multi-dimensional sequence database with timestamps |
| Graph database |
| Product transaction database |
| Event sequence |
| Interval sequence database |
| Uncertain transaction database |
| Double vectors (instances for clustering) |
| Time series |

### Dataset Viewer Tools

| Tool |
|------|
| Time series viewer |
| Cluster viewer |
| Graph / subgraph viewer (TKG, gSpan, cgSpan) |
| ARFF file viewer |
| Event sequence viewer |
| Sequence database viewer |
| Sequence database with cost binary utility viewer |
| Sequence database with cost numeric utility viewer |
| Sequence database with utility viewer |
| Time-extended sequence database viewer |
| Multi-dimensional sequence database viewer |
| Multi-dimensional time sequence database viewer |
| Transaction database viewer |
| Transaction database with cost utility viewer |
| Uncertain transaction database viewer |
| Utility transaction database viewer |
| Utility time transaction database viewer |
| Utility period transaction database viewer |
| Product transaction database viewer |
| Sequence database with time intervals viewer |
| Taxonomy file viewer |

### GUI Tools

| Tool |
|------|
| Algorithm Explorer |
| Memory Viewer (real-time memory usage) |
| Pattern Viewer (with frequency distributions) |
| Workflow Editor |
| Experiment Runner (vary parameters across algorithms) |
| SPMF Text Editor |
| Documentation Downloader |
| Pattern Diff Analyzer (contrast patterns) |
| Algorithm Graph Viewer (algorithm similarity graph) |

### Other Tools & Data Structures

| Category | Items |
|----------|-------|
| **Other Tools** | Export algorithm list to JSON |
| **Data Structures** | Red-black tree, Itemset-tree, Binary tree, KD-tree, Triangular matrix, Optimized primitive-type collections (hashmaps, lists, sets, etc.) |

## Datasets

To run experiments with SPMF, multiple datasets are provided on the SPMF website, in SPMF format:
[https://philippe-fournier-viger.com/spmf/index.php?link=datasets.php](https://philippe-fournier-viger.com/spmf/index.php?link=datasets.php)

---

## Architecture

A general overview of the architecture of SPMF is provided below.

<div align="center">
  <img src="images/spmf_architecture.jpg" alt="SPMF architecture"">
</div>

To use SPMF, a user can choose to use the Graphical interface, Command line interface or the SPMF-server. The user interacts with any of these interfaces to run algorithms which are managed by a module called the Agorithm Manager. There are mainly three types of algorithms, which are (1) data pre-processing algorithms, (2) data mining algorithms, and (3) algorithms to either visualize data or patterns found in the data. The Algorithm Manager has the list of all available algorithms, and a description of each algorithm. The description of an algorithm indicates how many parameters it has, what are the data  types of parameters, what is the algorithm name, etc. The input and output of algorithms are generally text files. A few different formats are supported, explained in the documentation of SPMF.

The source code is organized in several packages. The main packages are:
```
ca.pfv.spmf/
│
├── algorithms/
│   ├── associationrules/        → Association rule mining algorithms
│   ├── classifiers/             → Classification algorithms
│   ├── clustering/              → Clustering algorithms
│   ├── episodes/                → Episode mining algorithms
│   ├── frequentpatterns/        → Itemset mining algorithms
│   ├── graph_mining/            → Graph mining algorithms
│   ├── sequenceprediction/      → Sequence prediction algorithms
│   ├── sequential_rules/        → Sequential rule mining algorithms
│   ├── sequentialpatterns/      → Sequential pattern mining algorithms
│   ├── sort/                    → Sorting algorithms
│   └── timeseries/              → Time series mining & analysis algorithms
│
├── algorithmmanager/
│   ├── Algorithm Manager        → Central registry for algorithms
│   └── descriptions/            → Metadata (input/output types, authors, etc.)
│
├── datastructures/              → Specialized data structures (e.g., triangular matrix)

├── gui/                         → Graphical User Interface (MainWindow.java)
│   └── Main.java                → Command-line entry point
│
├── input/                       → Input file readers (transactions, sequences, etc.)
│
├── patterns/                    → Pattern representations (itemsets, rules, etc.)
│
├── test/                        → Example usage of algorithms (developer samples, not unit tests)
│
└── tools/                       → Utilities (generators, converters, statistics, etc.)
```
---

## How to Learn More?

- [The SPMF website](http://philippe-fournier-viger.com/spmf/)
- [The Data Blog](https://data-mining.philippe-fournier-viger.com/) — Blog from the founder of SPMF
- [Other Resources](https://www.philippe-fournier-viger.com/spmf/index.php?link=resources.php) — Books, tutorials, links to other projects, etc.

If you want to learn  about the theory Pattern Mining, watch the free [Pattern Mining Course](https://www.philippe-fournier-viger.com/COURSES/Pattern_mining/index.php) course:

<a href="https://www.philippe-fournier-viger.com/COURSES/Pattern_mining/index.php"><img src="images/course.png" alt="pattern mining course" width="200"></a>

and also check out the [@philfv YouTube channel](https://www.youtube.com/@philfv)

<a href="https://www.youtube.com/@philfv"><img src="images/youtube.png" alt="youtube channel" width="200"></a>

---

## Contributing

If you would like to contribute improvements, please contact the SPMF founder
at **philfv AT qq DOT com**. In particular, if you want to contribute new
algorithms not yet implemented in SPMF, you are very welcome to get in touch.

See the [contributors page](https://www.philippe-fournier-viger.com/spmf/index.php?link=contributors.php)
for a full list of people who have contributed to the project.

---

## License

The source code and files in this project are licensed under the **GNU General Public License v3.0 (GPLv3)**.
The GPL license grants four freedoms:

1. Run the program for any purpose
2. Access the source code
3. Modify the source code
4. Redistribute modified versions

**Restrictions:** If you redistribute the software (or derivative works), you must:

- Provide access to the source code
- License derivative works under the same GPLv3 license
- Include prominent notices stating that you modified the code, along with the modification date

For full details about the license and its requirements, see the [GPLv3 license](https://www.gnu.org/licenses/gpl-3.0.en.html).

---

## How to Cite SPMF

If you use SPMF in your research, please cite one of the following papers:

- Fournier-Viger, P., et al. (2012). *SPMF: A Java Open-Source Pattern Mining Library.  Journal of Machine Learning Research (JMLR).

- Fournier-Viger, P., Lin, C.W., Gomariz, A., Gueniche, T., Soltani, A., Deng, Z., Lam, H. T. (2016). *The SPMF Open-Source Data Mining Library Version 2.*  In Proceedings of the 19th European Conference on Principles of Data Mining and Knowledge Discovery (PKDD 2016), Part III, Springer LNCS 9853, pp. 36–40.

For a full list of citations, see the
[citations page](https://www.philippe-fournier-viger.com/spmf/index.php?link=citations.php).
Citing SPMF helps support the project — thank you! 🙏

---

## Authors

**Project Leaders:**

- **Prof. Philippe Fournier-Viger** (Founder), 
  [https://www.philippe-fournier-viger.com/](https://www.philippe-fournier-viger.com/)
  (e-mail: philfv AT qq DOT com)
- **Prof. Jerry Chun-Wei Lin**
- **Prof. Wei Song** — North China University of Technology, Beijing, China
- **Prof. Vincent S. Tseng** — National Chiao Tung University, Taiwan
- **Prof. Ji Zhang** — University of Southern Queensland, Australia, 
  [https://staffprofile.unisq.edu.au/Profile/Ji-Zhang](https://staffprofile.unisq.edu.au/Profile/Ji-Zhang)

**Contributors:**
A full list of all contributors can be found at:
[https://www.philippe-fournier-viger.com/spmf/index.php?link=contributors.php](https://www.philippe-fournier-viger.com/spmf/index.php?link=contributors.php)

The content of this page is copyright Philippe Fournier-Viger and contributors
