# Introduction #
This page explains the sample usage of the tool.


The tool is developed using jackrabbit standalone 2.2.7 as dependency.  The code makes use of the TransientRepository for verifying the cnd file.  Please deploy [Jackrabbit](http://jackrabbit.apache.org/downloads.html) 2.2.7 to your local or remote maven repository  before building the codebase.

The executable is a bundle packaged using [maven-bundle-plugin](http://felix.apache.org/site/apache-felix-maven-bundle-plugin-bnd.html). Please look at the pom file for more details.

# Using as standalone #

## CLI ##

```
usage: java -jar cnd2xsd-<version>.jar
 -fc <arg>        Path for the input cnd file
 -fp <arg>      Path for properties map.
 -fx <arg>      Path for generating XML schema.
 -help          Prints this list.
 -ns <arg>      The namespace for the XSD.
 -nsp <arg>     The namespace prefix.
 -r <arg>       The root element in the XSD.
 -rtype <arg>   The root element type.

```


## Example 1 ##

For the following cnd: (car.cnd)
```
<car='http://www.modeshape.org/examples/cars/1.0'>
[car:Car] > nt:unstructured, mix:created
  - car:maker (string)
  - car:model (string)
  - car:year (string) < '(19|20)\d{2}'  // any 4 digit number starting with '19' or '20'
  - car:msrp (string) < '[$]\d{1,3}[,]?\d{3}([.]\d{2})?'   // of the form "$X,XXX.ZZ", "$XX,XXX.ZZ" or "$XXX,XXX.ZZ" 
                                                           // where '.ZZ' is optional
  - car:userRating (long) < '[1,5]'                        // any value from 1 to 5 (inclusive)
  - car:valueRating (long) < '[1,5]'                       // any value from 1 to 5 (inclusive)
  - car:mpgCity (long) < '(0,]'                            // any value greater than 0
  - car:mpgHighway (long) < '(0,]'                         // any value greater than 0
  - car:lengthInInches (double) < '(0,]'                   // any value greater than 0
  - car:wheelbaseInInches (double) < '(0,]'                // any value greater than 0
  - car:engine (string)
  - car:alternateModels (reference)  < 'car:Car'

[car:Cars] 
  + * (car:Car) multiple 
```

and the following property map ( propmap.txt)

```
nt:unstructured#
mix:created#created,createdBy
mix:versionable#
nt:base#
```

The following CLI :
```
java -jar target/cnd2xsd-R1.0.0-SNAPSHOT.jar -fc car.cnd  -fx car.xsd  -fp propmap.txt -r cars -rtype Cars -ns http://www.cars.com/cars/v1.0 -nsp car
```

creates the following XSD:

```
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified" targetNamespace="http://www.cars.com/cars/v1.0">
    <xs:element xmlns="http://www.cars.com/cars/v1.0" name="cars" type="Cars"/>
    <xs:complexType name="Car">
        <xs:sequence>
            <xs:element xmlns="http://www.cars.com/cars/v1.0" maxOccurs="1" minOccurs="1" name="created" type="created"/>
        </xs:sequence>
        <xs:attribute name="lengthInInches" type="xs:string"/>
        <xs:attribute name="msrp" type="xs:string"/>
        <xs:attribute name="model" type="xs:string"/>
        <xs:attribute name="engine" type="xs:string"/>
        <xs:attribute name="year" type="xs:string"/>
        <xs:attribute name="wheelbaseInInches" type="xs:string"/>
        <xs:attribute name="alternateModels" type="xs:string"/>
        <xs:attribute name="valueRating" type="xs:long"/>
        <xs:attribute name="maker" type="xs:string"/>
        <xs:attribute name="mpgHighway" type="xs:long"/>
        <xs:attribute name="mpgCity" type="xs:long"/>
        <xs:attribute name="userRating" type="xs:long"/>
        <xs:attribute name="nodename" type="xs:string"/>
    </xs:complexType>
    <xs:complexType name="created">
        <xs:attribute name="created" type="xs:string"/>
        <xs:attribute name="createdBy" type="xs:string"/>
    </xs:complexType>
    <xs:complexType name="Cars">
        <xs:sequence>
            <xs:element xmlns="http://www.cars.com/cars/v1.0" maxOccurs="unbounded" minOccurs="0" name="Car" type="Car"/>
        </xs:sequence>
    </xs:complexType>
</xs:schema>
```