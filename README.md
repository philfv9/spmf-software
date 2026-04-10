[![License](https://img.shields.io/github/license/philfv9/spmf-software.svg)](https://github.com/philfv9/spmf-software/blob/main/LICENSE)
[![Release](https://img.shields.io/github/v/release/philfv9/spmf-software.svg)](https://github.com/philfv9/spmf-software/releases/latest)
[![Stars](https://img.shields.io/github/stars/philfv9/spmf-software.svg)](https://github.com/philfv9/spmf-software/stargazers)

<div align="center">
  <h1>The SPMF Open-Source Pattern Mining Sofware</h1>
  <img src="images/spmf.png" alt="SPMF Logo" width="200">
</div>

**[SPMF](http://philippe-fournier-viger.com/spmf/)** is a popular **open-source data mining software and library** written in **Java**, specializing in **pattern mining**. It provides over **300 algorithms** for various tasks such as:

- Frequent itemset mining
- Association rule mining
- Sequential pattern mining
- Sequential rule mining
- High-utility itemset mining
- Episode mining
- Graph mining
- Time series analysis
- and more

SPMF is designed for both **researchers and practitioners**, offering a simple API, a graphical user interface (GUI), and command-line tools. It is lightweight with **no external dependencies**.

**Current version:** `v2.65` (released February 18, 2026)

> **Full documentation, tutorials, downloads, and support:**
> 👉 [http://philippe-fournier-viger.com/spmf/](http://philippe-fournier-viger.com/spmf/)

---

## Table of Contents

- [Ways to Use SPMF](#ways-to-use-spmf)
- [Documentation](#documentation)
- [Datasets](#datasets)
- [Screenshots](#screenshots)
- [Related Resources](#related-resources)
- [Contributing](#contributing)
- [License](#license)
- [How to Cite SPMF](#how-to-cite-spmf)
- [Authors](#authors)

---

## Ways to Use SPMF

SPMF can be used in several ways depending on your needs:

---

### 1 — Graphical User Interface (GUI)

Launch `spmf.jar` directly to open the built-in Swing GUI. No programming
required — select an algorithm, set parameters, and run.

<div align="center">
  <img src="case1.png" alt="SPMF GUI use case">
  <br>
  <em>SPMF Graphical User Interface</em>
</div>

For example:

```bash
java -jar spmf.jar
```

See the [download page](https://philippe-fournier-viger.com/spmf/index.php?link=download.php)
for installation instructions.

---

### 2 — Command Line

Run any algorithm directly from the terminal without opening the GUI:

<div align="center">
  <img src="case2.png" alt="SPMF terminal use case">
  <br>
  <em>SPMF Graphical User Interface</em>
</div>

For example: 

```bash
java -jar spmf.jar run Apriori input.txt output.txt 0.4
```

See the [documentation](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php)
for the full command-line syntax for each algorithm.

---

### 3 — Java API

Integrate SPMF algorithms directly into your Java project by calling the
algorithm classes programmatically. No external dependencies are required —
just add `spmf.jar` to your classpath.

<div align="center">
  <img src="case3.png" alt="SPMF API use case">
  <br>
  <em>SPMF Graphical User Interface</em>
</div>

See the [documentation](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php)
for Java API usage examples.

---

### 4 — Wrappers for Other Languages

SPMF can be called from **Python, R, C#, and more** via community-provided
wrappers that invoke the command-line interface:

<div align="center">
  <img src="case4.png" alt="SPMF wrappers use case">
  <br>
  <em>SPMF Graphical User Interface</em>
</div>

👉 [SPMF Wrappers page](https://www.philippe-fournier-viger.com/spmf/index.php?link=spmfwrappers.php)

---

### 5 — REST API via SPMF-Server *(new)*

**[SPMF-Server](https://github.com/philfv9/spmf-server)** is a lightweight
HTTP server that wraps the SPMF library and exposes all algorithms as a
**REST API**. This lets any language or tool submit mining jobs over HTTP and
retrieve results without needing a local Java integration.

<div align="center">
  <img src="images/spmf-server.png" alt="SPMF-Server framework">
  <br>
  <em>SPMF Graphical User Interface</em>
</div>

This can be useful to run SPMF on a remote machine and query it from a client or integrate it into a web application or microservice. See these projects for details: 
| Project | Description |
|---|---|
| [spmf-server](https://github.com/philfv9/spmf-server) | The SPMF-Server REST API server (Java) |
| [spmf-server-pythonclient](https://github.com/philfv9/spmf-server-pythonclient) | Ready-to-use Python CLI and GUI clients for SPMF-Server |

## Documentation

- **Installation and quick start:**
  [download page](https://philippe-fournier-viger.com/spmf/index.php?link=download.php)

- **Main documentation** (with examples for each algorithm):
  [https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php](https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php)

- **FAQ:**
  [https://philippe-fournier-viger.com/spmf/index.php?link=FAQ.php](https://philippe-fournier-viger.com/spmf/index.php?link=FAQ.php)

- **List of algorithms:**
  [https://philippe-fournier-viger.com/spmf/index.php?link=algorithms.php](https://philippe-fournier-viger.com/spmf/index.php?link=algorithms.php)

---

## Datasets

Datasets in SPMF format are available on the SPMF website:
[https://philippe-fournier-viger.com/spmf/index.php?link=datasets.php](https://philippe-fournier-viger.com/spmf/index.php?link=datasets.php)

---

## Screenshots

<div align="center">
  <img src="images/spmf_small3.jpg" alt="SPMF GUI Screenshot" width="600">
  <br>
  <em>SPMF Graphical User Interface</em>
</div>

---

## Related Resources

- [The SPMF website](http://philippe-fournier-viger.com/spmf/)
- [SPMF-Server](https://github.com/philfv9/spmf-server) — REST API server for SPMF
- [spmf-server-pythonclient](https://github.com/philfv9/spmf-server-pythonclient) — Python CLI and GUI clients for SPMF-Server
- [The Pattern Mining Course](https://data-mining.philippe-fournier-viger.com/COURSES/Pattern_mining/index.php) — A free online course covering pattern mining algorithms and their implementation
- [More Pattern Mining Videos on the @philfv YouTube channel](https://www.youtube.com/@philfv)
- [The Data Blog](https://data-mining.philippe-fournier-viger.com/) — Blog from the founder of SPMF
- [Other Resources](https://www.philippe-fournier-viger.com/spmf/index.php?link=resources.php) — Books, tutorials, links to other projects, etc.

---

## Contributing

If you would like to contribute improvements, please contact the SPMF founder
at **philfv AT qq DOT com**. In particular, if you want to contribute new
algorithms not yet implemented in SPMF, you are very welcome to get in touch.

See the [contributors page](https://www.philippe-fournier-viger.com/spmf/index.php?link=contributors.php)
for a full list of people who have contributed to the project.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.
The GPL license grants four freedoms:

1. Run the program for any purpose
2. Access the source code
3. Modify the source code
4. Redistribute modified versions

**Restrictions:** If you redistribute the software (or derivative works), you must:

- Provide access to the source code
- License derivative works under the same GPLv3 license
- Include prominent notices stating that you modified the code, along with the modification date

For full details, see the [GPLv3 license](https://www.gnu.org/licenses/gpl-3.0.en.html).

---

## How to Cite SPMF

If you use SPMF in your research, please cite one of the following papers:

> Fournier-Viger, P., et al. (2012). *SPMF: A Java Open-Source Pattern Mining Library.*
> Journal of Machine Learning Research (JMLR).

> Fournier-Viger, P., Lin, C.W., Gomariz, A., Gueniche, T., Soltani, A., Deng, Z.,
> Lam, H. T. (2016). *The SPMF Open-Source Data Mining Library Version 2.*
> In Proceedings of the 19th European Conference on Principles of Data Mining and
> Knowledge Discovery (PKDD 2016), Part III, Springer LNCS 9853, pp. 36–40.

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
