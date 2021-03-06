[![](https://img.shields.io/github/license/creditdatamw/zerocell.svg)](./LICENSE)
[![](https://img.shields.io/maven-central/v/com.creditdatamw.labs/zerocell-core.svg)](http://mvnrepository.com/artifact/com.creditdatamw.labs/zerocell-core)

ZeroCell
========


Existing Excel libraries do too much just to read data from a workbook.
This library is optimized for *reading* data from excel only.
Particularly, it is optimized for getting the data from an Excel Sheet
into POJOs (Plain Old Java Objects) and has a very simple API. 

## Goals 

* Get POJOs from Excel with lower overheads i.e. read excel files with 
as few resources as possible
* Provide mappings for POJOs to excel rows via annotations
* Generate Excel readers via a compile-time annotation processor
* Provide a simple low-ceremony API

## Non-Goals

* Read or process excel workbook styles and other visual effects
* Writing to excel files
* Loading data into complex object hierarchies.

## Why not handle writing?

The Apache POI project has a good API for dealing with excel files and
provides the `SXSSFWorkbook` for writing large files in an efficient manner.

## Usage

There are three ways to use zerocell: via annotations, the programmatic api and using the annotation processor.

First things first, add the following dependency to your `pom.xml`

```xml
<dependency>
    <groupId>com.creditdatamw.labs</groupId>
    <artifactId>zerocell-core</artifactId>
    <version>0.3.0</version>
</dependency>
```

### Using Annotations

You create a class with `@Column` (and optionally `@RowNumber`) 
annotations to represent a row in an Excel sheet and
then use the static methods on the `Reader` class to read the 
list of data from the file.

For example:

```java
public class Person {
    @RowNumber
    private int rowNumber;
    
    @Column(index=0, name="FIRST_NAME")
    private String firstName;
    
    @Column(index=1, name="LAST_NAME")
    private String lastName;
    
    @Column(index=2, name="DATE_OF_BIRTH")
    private LocalDate dateOfBirth;
    
    // Getters and setters here ...
    
    public static void main(String... args) {
        // Then using the `Reader` class you can load 
        // a list from the excel file as follows:
        List<Person> people = Reader.of(Person.class)
                            .from(new File("people.xlsx"))
                            .sheet("Sheet 1")
                            .list();
        
        // You can also inspect the column names of 
        // the class using the static `columnsOf` method:
        String[] columns = Reader.columnsOf(Person.class);    
    }
}
```

### Using the Programmatic API

Since version `0.3.0` zerocell provides a non-annotation based API. 
This allows you to work with your existing POJOs without having
to change your sources. The only difference with the annotation based
API is that you have to define the column mappings using the `Reader.using`
method.

For example:

```java
public class Person {
    private int rowNumber;
    
    private String firstName;
    
    private String lastName;
    
    private LocalDate dateOfBirth;
    
    // Getters and setters here ...
    
    public static void main(String... args) {
        // Then using the `Reader` class you can load 
        // a list from the excel file as follows:
        List<Person> people = Reader.of(Person.class)
                            .from(new File("people.xlsx"))                            
                            .using(
                                new RowNumberInfo("rowNumber", Integer.class),
                                new ColumnInfo("ID", "id", 0, String.class),
                                new ColumnInfo("FIRST_NAME", "firstName", 1, String.class),
                                new ColumnInfo("MIDDLE_NAME", "middleName", 2, String.class),
                                new ColumnInfo("LAST_NAME", "lastName", 3, String.class),
                                new ColumnInfo("DATE_OF_BIRTH", "dateOfBirth", 4, LocalDate.class),
                                new ColumnInfo("DATE_REGISTERED", "dateOfRegistration", 6, Date.class),
                                new ColumnInfo("FAV_NUMBER", "favouriteNumber", 5, Integer.class)
                            )
                            .sheet("Sheet 1")
                            .list();
         
        people.forEach(person -> {
            // Do something with person here    
        });    
    }
}
```

### Using the Annotation Processor

ZeroCell provides an annotation processor to generate Reader 
classes to read records from Excel without Runtime reflection 
which makes the code amenable to better auditing and customization.

In order to use the functionality you will first need to add 
the dependency to your POM. This adds a compile-time 
annotation processor which generates the implementation classes. 

```xml
<dependency>
    <groupId>com.creditdatamw.labs</groupId>
    <artifactId>zerocell-processor</artifactId>
    <version>0.3.0</version>
    <scope>provided</scope>
</dependency>
```

Then, in your code use the `@ZerocellReaderBuilder` annotation on a class
that contains ZeroCell `@Column` annotations.

Using a class defined as in the example shown below:

```java
package com.example;

@ZerocellReaderBuilder
public class Person {
    @RowNumber
    private int rowNumber;
    
    @Column(index=0, name="FIRST_NAME")
    private String firstName;
    
    @Column(index=1, name="LAST_NAME")
    private String lastName;
    
    @Column(index=2, name="DATE_OF_BIRTH")
    private LocalDate dateOfBirth;
    
    public static void main(String... args) {
        File file = new File("people.xlsx");
        String sheet = "Sheet 1";
        ZeroCellReader<Person> reader = new com.example.PersonReader();
        List<Person> people = reader.read(file, sheet);
        people.forEach(person -> {
            // do something with person
        });
    }
}
```

Generates a class in the com.example package

```java
package com.example;

public class PersonReader implements ZeroCellReader {
  // generated code here
}
```

## Exception Handling

The API throws `ZeroCellException` if something goes wrong, e.g. sheet not found. 
It is an unchecked exception and may cause your code to stop executing if not 
handled. Typically `ZeroCellException` will wrap another exception, so it's worth 
peeking at the cause using `Exception#getCause`.

## CONTRIBUTING

See the [`CONTRIBUTING.md`](CONTRIBUTING.md) file for more information.

---

Copyright (c) 2017, Credit Data CRB Ltd
