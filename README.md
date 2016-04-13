# featured-gml [![Build Status](https://travis-ci.org/PDOK/featured-gml.svg?branch=master)](https://travis-ci.org/PDOK/featured-gml)

Translates xml/gml input to json files which can be handled by featured. 

The resulting files are stored on the system in the directory set by the property "featured-gml.jsonstore". If this property is not set, the temporary directory is used.

## Example usage

# Starting a run
Do a POST to api/xml2json with the following body. Note this mapping is stored in the catalogus. 
```json
{
    "dataset" : "bestuurlijkegrenzen",
    "mapping" : "{:Gemeenten #xml2json/mappedcollection {:type :new :mapping
                    [
                     [:_id :s/id-attr]
                     [:_collection :s/tag clojure.string/lower-case]
                     :Code
                     :Gemeentenaam
                      [:_geometry [:surfaceProperty :s/inner-gml][:multiSurfaceProperty :s/inner-gml]]
                     ]},
 :Landsgrens #xml2json/mappedcollection {:type :new :mapping
                    [
                     [:_id :s/id-attr]
                     [:_collection :s/tag clojure.string/lower-case]
                     :Code
                     :Landsnaam
                      [:_geometry [:surfaceProperty :s/inner-gml][:multiSurfaceProperty :s/inner-gml]]
                     ]},
 :Provincies #xml2json/mappedcollection {:type :new :mapping
                    [
                     [:_id :s/id-attr]
                     [:_collection :s/tag clojure.string/lower-case]
                     :Provincienaam
                      [:_geometry [:surfaceProperty :s/inner-gml][:multiSurfaceProperty :s/inner-gml]]
                     ]}
}",
    "file" : "http://localhost:8084/201502.zip",
    "validity": "2013-10-21T13:28:06.419Z"
}
```

There is both a synchronous and an asynchronous approach. **If in the POST request, the "callback" property is set, the request is handled asynchronously.** 

A successfull response looks like:
```json
{
  "json-files": [
    "201602261324_Provinciegrenzen.json.zip",
    "201602261324_Landsgrens.json.zip",
    "201602261324_Gemeentegrenzen.json.zip"
  ]
}
```

# Getting the result
Use the json-file name in a GET request. Example:
```
/api/get/201602261324_Landsgrens.json.zip
```
Files are available for 48 hours after they have been created using xml2json

## Building
```lein build```

## License

Copyright © 2015 PDOK

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
