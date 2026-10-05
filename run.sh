#!/bin/sh
mkdir -p out && javac -d out src/ResearchConnect.java && java -Xmx3g -cp out ResearchConnect data/arXiv_scientific_dataset.csv 8080
