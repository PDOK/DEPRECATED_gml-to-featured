# GML-to-Featured [![Build Status](https://travis-ci.org/PDOK/gml-to-featured.svg?branch=master)](https://travis-ci.org/PDOK/gml-to-featured)

Open-source GML/XML to JSON transformation software for Featured, developed by Publieke Dienstverlening op de Kaart
(PDOK).

GML-to-Featured is one of the input applications in the PDOK ETL landscape.
It transforms GML/XML input to JSON files that can be processed by Featured (<https://github.com/PDOK/featured>).
Apart from the GML/XML, GML-to-Featured's input is a mapping file that describes the transformation.

## Usage

GML-to-Featured supports a server mode and a command-line mode.
To run it locally, the following command can be used to start it in server mode:

    $ lein ring server-headless

To use the command line locally, use:

    $ lein run [args]

or

    $ lein uberjar
    $ java -jar gml-to-featured-1.0.6-standalone.jar [args]

## Examples

### REST API

#### Starting a run
Do a POST to `api/xml2json` with the following body.
Note this mapping is stored in the catalogus.
```json
{
    "dataset" : "bestuurlijkegrenzen",
    "mapping" : "{:config/sequence-element :FeatureCollection
                  :config/translators
                    {#xml2json/path [:featureMember :Gemeenten]
                     #xml2json/mapped
                         {:type :new :mapping
                                [
                                 [:_id :s/id-attr]
                                 [:_collection :s/tag clojure.string/lower-case]
                                 [:code [:Code]]
                                 [:gemeentenaam [:Gemeentenaam]]
                                 [:_geometry [:surfaceProperty :s/inner-gml] [:multiSurfaceProperty :s/inner-gml]]
                                 ]},
                     #xml2json/path [:featureMember :Landsgrens]
                     #xml2json/mapped
                         {:type :new :mapping
                                [
                                 [:_id :s/id-attr]
                                 [:_collection :s/tag clojure.string/lower-case]
                                 [:code [:Code]]
                                 [:landsnaam [:Landsnaam]]
                                 [:_geometry [:surfaceProperty :s/inner-gml] [:multiSurfaceProperty :s/inner-gml]]
                                 ]},
                     #xml2json/path [:featureMember :Provincies]
                     #xml2json/mapped
                         {:type :new :mapping
                                [
                                 [:_id :s/id-attr]
                                 [:_collection :s/tag clojure.string/lower-case]
                                 [:provincienaam [:Provincienaam]]
                                 [:_geometry [:surfaceProperty :s/inner-gml] [:multiSurfaceProperty :s/inner-gml]]
                                 ]}
                     }
                  }",
    "file" : "http://localhost:8084/201502.zip",
    "validity": "2013-10-21T13:28:06.419Z"
}
```

**The request is handled asynchronously. If no callback is set, you will not get any result.**

A successful response looks like:
```json
{
  "json-files": [
    "201602261324_Provinciegrenzen.json.zip",
    "201602261324_Landsgrens.json.zip",
    "201602261324_Gemeentegrenzen.json.zip"
  ]
}
```

#### Getting the result
Use the JSON file name in a GET request. Example:
```
/api/get/201602261324_Landsgrens.json.zip
```
Files are available for 48 hours after creation.

### Command-line interface

#### Starting a run
Run the following command in the root folder of the project, filling in the listed parameters:
```
lein run -- --validity [timestamp]
            [dataset name]
            [json configuration file for the mapping]
            [source file with features for transformation]
            [target file for the transformed features]
```

```
lein run -- --validity='2016-11-11T11:11:11.000' bestuurlijkegrenzen landsgrens.config Landsgrens.gml landsgrens.json
```

```
{:config/sequence-element   :FeatureCollection
 :config/feature-identifier #xml2json/comp [first :content]
 :config/feature-selector   #xml2json/comp [first :content]
 :config/translators
   {#xml2json/path [:featureMember :Landsgrens]
    #xml2json/mapped
        {:type :new :mapping
               [
                [:_id :s/id-attr]
                [:_collection :s/tag clojure.string/lower-case]
                [:code [:Code]]
                [:landsnaam [:Landsnaam]]
                [:_geometry [:surfaceProperty :s/inner-gml] [:multiSurfaceProperty :s/inner-gml]]
                ]}}
 }
```

#### Getting the result
Files generated through the command line are stored in the project root folder, when no specific path is given.

## Mapping file

The mapping used for the GML/XML-to-JSON transformation uses the [EDN](https://github.com/edn-format/edn) data format.
The mapping consists of four configuration parameters:

- `:config/sequence-element`
- `:config/feature-identifier`
- `:config/feature-selector`
- `:config/translators`

### `:config/sequence-element`
Specifies the GML/XML element that contains the sequence of features that need to be processed.
In the example below, the `sequence-element` would be `:FeatureCollection`, because the element `gml:FeatureCollection`
contains the desired features of type `gml:featureMember`.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<gml:FeatureCollection
    xmlns:kad="http://www.kadaster.nl/kad/pdok"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:gml="http://www.opengis.net/gml/3.2"
    gml:id="id38504fe7-9063-4ca0-b945-965a8665993b"
    xsi:schemaLocation="http://www.kadaster.nl/kad/pdok Landsgrens.xsd">
    <gml:featureMember>
        <kad:Landsgrens gml:id="id63e007bd-2933-4bd2-9c70-09f00099c2cb">
            <kad:Code>6030</kad:Code>
            <kad:Landsnaam>Nederland</kad:Landsnaam>
            <gml:multiSurfaceProperty>
                ...
            </gml:multiSurfaceProperty>
        </kad:Landsgrens>
    </gml:featureMember>
    <gml:featureMember>
        ...
    </gml:featureMember>
    <gml:featureMember>
        ...
    </gml:featureMember>
</gml:FeatureCollection>
```

In the example below, the `sequence-element` would be `:GemeenteWoonplaatsRelatieProduct`, because the element
`gml:GemeenteWoonplaatsRelatieProduct` contains the desired features of type `gml:GemeenteWoonplaatsRelatie`.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<gwr-bestand:BAG-GWR-Deelbestand-LVC
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:gwr-bestand="http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-deelbestand-lvc/v20120701"
    xmlns:selecties-extract="http://www.kadaster.nl/schemas/bag-verstrekkingen/extract-selecties/v20110901"
    xmlns:bagtype="http://www.kadaster.nl/schemas/imbag/imbag-types/v20110901"
    xmlns:gwr-product="http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-producten-lvc/v20120701"
    xmlns:gwr_LVC="http://www.kadaster.nl/schemas/bag-gwr-model/lvc/v20120701"
    xmlns:gwr_gemeente="http://www.kadaster.nl/schemas/bag-gwr-model/gemeente/v20120701"
    xsi:schemaLocation="http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-deelbestand-lvc/v20120701
        http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-deelbestand-lvc/v20120701/BagvsGwrDeelbestandLvc-1.4.xsd">
    <gwr-bestand:antwoord>
        <gwr-bestand:producten>
            <gwr-product:GemeenteWoonplaatsRelatieProduct>
                <gwr_LVC:GemeenteWoonplaatsRelatie>
                    <gwr_LVC:tijdvakgeldigheid>
                        <bagtype:begindatumTijdvakGeldigheid>2010101900000000</bagtype:begindatumTijdvakGeldigheid>
                        <bagtype:einddatumTijdvakGeldigheid>2010102000000000</bagtype:einddatumTijdvakGeldigheid>
                    </gwr_LVC:tijdvakgeldigheid>
                    <gwr_LVC:gerelateerdeWoonplaats>
                        <gwr_LVC:identificatie>3386</gwr_LVC:identificatie>
                    </gwr_LVC:gerelateerdeWoonplaats>
                    <gwr_LVC:gerelateerdeGemeente>
                        <gwr_LVC:identificatie>0003</gwr_LVC:identificatie>
                    </gwr_LVC:gerelateerdeGemeente>
                    <gwr_LVC:status>voorlopig</gwr_LVC:status>
                </gwr_LVC:GemeenteWoonplaatsRelatie>
                <gwr_LVC:GemeenteWoonplaatsRelatie>
                    ...
                </gwr_LVC:GemeenteWoonplaatsRelatie>
                <gwr_LVC:GemeenteWoonplaatsRelatie>
                    ...
                </gwr_LVC:GemeenteWoonplaatsRelatie>
            </gwr-product:GemeenteWoonplaatsRelatieProduct>
        </gwr-bestand:producten>
    </gwr-bestand:antwoord>
</gwr-bestand:BAG-GWR-Deelbestand-LVC>
```

### `:config/feature-identifier`
This parameter is optional.
When the source GML/XML file contains the features to map at a 'lower' level in the document, this parameter can be set
(together with the `:config/feature-selector`) so the translator mapping is applied at the right level.
Currently, the only value permitted is `#xml2json/comp [first :content]`.
This value 'first' is interpreted as the Clojure function `clojure.core/first`.
When the source document is read, a tree map is constructed from the level configured in the parameter
`:config/sequence-element`.
By default, the features one level deeper are read by the application, so the application will search a translator for
`:featureMember` (when taking the bestuurlijkegrenzen REST API example).
To make sure the right translator is selected, we need to go to the right document level `:Landsgrens`.
To achieve that action, the configuration parameter `:config/feature-identifier` contains the hint `#xml2json/comp
[first :content]`.

### `:config/feature-selector`
This parameter is optional.
Like the `:config/feature-identifier`, this parameter can be set so the 'right' feature is selected on which the
translation is performed.
Currently, the only value permitted is `#xml2json/comp [first :content]`.
This is used in the same way as the parameter `:config/feature-identifier`.

### `:config/translators`
Contains the different translator configurations that can be used during the transformation process.
Multiple translators can be supplied.

## License

Copyright © 2015-2017 Publieke Dienstverlening op de Kaart

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
