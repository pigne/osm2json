# osm2json

A simplistic and opiniated OSM parser that creates a subset version of the given map in some undocumented-home-made JSON format. 

## Compile & execute 

using Maven: 

```bash
mvn compile
mvn exec:java -Dexec.mainClass=org.pigne.Osm2json -Dexec.args="data/eure.osm.xml data/eure.json"
```

