ZeroCell
========

Existing Excel libraries do too much just to read data from a workbook.
This library is optimized for *reading* data from excel only.
Particularly, it is optimized for getting the data from Excel into
POJO (Plain Old Java Objects). 

## Goals 

* Get data from Excel with lower overheads
* Provide methods for mapping excel rows to POJOs
* Read excel files with as few resources as possible
* Provide mappings for POJOs to excel rows via annotations

## Non-Goals

* Read or process excel workbook styles and other visual effects
* Writing to excel files


## Why not handle writing?

The Apache POI project has a good API for dealing with excel files and
provides the `SXSSFWorkbook` for writing large files in an efficient manner.


## Authors

* Zikani Nyirenda Mwase <zikani@creditdatamw.com>

---

Copyright (c) 2017, Credit Data CRB Ltd