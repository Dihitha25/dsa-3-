@echo off
if not exist out mkdir out
javac -d out src\ResearchConnect.java || exit /b 1
java -Xmx3g -cp out ResearchConnect data\arXiv_scientific_dataset.csv 8080
