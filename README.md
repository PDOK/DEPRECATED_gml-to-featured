# gml-to-featured [![Build Status](https://travis-ci.org/PDOK/gml-to-featured.svg?branch=master)](https://travis-ci.org/PDOK/gml-to-featured)

Translates xml/gml input to json files which can be handled by [featured] (https://github.com/PDOK/featured). 

## Example usage

# Starting a run

## On the REST api 

Do a POST to api/xml2json with the following body. Note this mapping is stored in the catalogus. 
```json
{
    "dataset" : "bestuurlijkegrenzen",
    "mapping" : "{:config/sequence-element :FeatureCollection
				  :config/feature-identifier #xml2json/comp [first :content]
				  :config/feature-selector #xml2json/comp [first :content]
				  :config/translators 	
					{#xml2json/path [:Gemeenten] #xml2json/mapped {:type :new :mapping
						[
						 [:_id :s/id-attr]
						 [:_collection :s/tag clojure.string/lower-case]
						 :Code
						 :Gemeentenaam
						  [:_geometry [:surfaceProperty :s/inner-gml][:multiSurfaceProperty :s/inner-gml]]
						 ]},
					#xml2json/path [:Landsgrens] #xml2json/mapped {:type :new :mapping
						[
						 [:_id :s/id-attr]
						 [:_collection :s/tag clojure.string/lower-case]
						 :Code
						 :Landsnaam
						  [:_geometry [:surfaceProperty :s/inner-gml][:multiSurfaceProperty :s/inner-gml]]
						 ]},
					#xml2json/path [:Provincies] #xml2json/mapped {:type :new :mapping
						[
						 [:_id :s/id-attr]
						 [:_collection :s/tag clojure.string/lower-case]
						 :Provincienaam
						  [:_geometry [:surfaceProperty :s/inner-gml][:multiSurfaceProperty :s/inner-gml]]
						 ]}
					}
}",
    "file" : "http://localhost:8084/201502.zip",
    "validity": "2013-10-21T13:28:06.419Z"
}
```

**The request is handled asynchronously. If no callback is set, you will not get any result**

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

### Getting the result
Use the json-file name in a GET request. Example:
```
/api/get/201602261324_Landsgrens.json.zip
```
Files are available for 48 hours after they have been created using xml2json

## On the commandline

Do a commandline command in the root folder of the project with the following parameters.
lein run -- [dataset name] 
			[json configuration file for the mapping] 
			[timestamp] 
			[source file with features for transformation] 
			[target file for the transformed features]

```
lein run -- bestuurlijkegrenzen landsgrens.config '2016-11-11T11:11:11.000' Landsgrens.gml landsgrens.json
```

```
{:config/sequence-element :FeatureCollection
			 :config/feature-identifier #xml2json/comp [first :content]
			 :config/feature-selector #xml2json/comp [first :content]
			 :config/translators { #xml2json/path [:Landsgrens] #xml2json/mapped 
				{:type :new :mapping
                    [
                     [:_id :s/id-attr]
                     [:_collection :s/tag clojure.string/lower-case]
                     :Code
                     :Landsnaam
                      [:_geometry [:surfaceProperty :s/inner-gml][:multiSurfaceProperty :s/inner-gml]]
                     ]}}	
}
```

### Getting the result
Files generated through the commandline are stored in the project root folder, when no specific path is given.

# Mapping file

Gml-to-featured translates xml/gml input to json files which can be handled by featured, this feature is achieved by the mapping that is used for a translation. 
The mapping is an [EDN](https://github.com/edn-format/edn) data format. The mapping consists of four configuration parameters:
. :config/sequence-element
. :config/feature-identifier
. :config/feature-selector
. :config/translators

## :config/sequence-element
Specifices the xml/gml element that contains the sequence of features which needs to be processed.
In the example below the sequence-element would be :FeatureCollection, because below the element gml:FeatureCollection starts the sequence with features of the type gml:featureMember.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<gml:FeatureCollection xmlns:kad="http://www.kadaster.nl/kad/pdok" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:gml="http://www.opengis.net/gml/3.2" gml:id="id38504fe7-9063-4ca0-b945-965a8665993b" xsi:schemaLocation="http://www.kadaster.nl/kad/pdok Landsgrens.xsd">
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

In the example below the sequence-element would be :GemeenteWoonplaatsRelatieProduct, because below the element gml:GemeenteWoonplaatsRelatieProduct starts the sequence with features of the type gml:GemeenteWoonplaatsRelatie.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<gwr-bestand:BAG-GWR-Deelbestand-LVC xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:gwr-bestand="http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-deelbestand-lvc/v20120701" xmlns:selecties-extract="http://www.kadaster.nl/schemas/bag-verstrekkingen/extract-selecties/v20110901" xmlns:bagtype="http://www.kadaster.nl/schemas/imbag/imbag-types/v20110901" xmlns:gwr-product="http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-producten-lvc/v20120701" xmlns:gwr_LVC="http://www.kadaster.nl/schemas/bag-gwr-model/lvc/v20120701" xmlns:gwr_gemeente="http://www.kadaster.nl/schemas/bag-gwr-model/gemeente/v20120701" xsi:schemaLocation="http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-deelbestand-lvc/v20120701 http://www.kadaster.nl/schemas/bag-verstrekkingen/gwr-deelbestand-lvc/v20120701/BagvsGwrDeelbestandLvc-1.4.xsd">
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

## :config/feature-identifier
This parameter is optional. 
When the source xml/gml file contains the features to map at a 'lower' level in the document this parameter can be set (together with the :config/feature-selector) so the translator mapping is applied at the right level.
Currently the only value permited is '#xml2json/comp [first :content]'. This value 'first' is interpreted as the clojure function clojure.core/first. 
When the source document is read a tree-map is made from the level configured in the parameter :config/sequence-element. As default the features one level deeper are read by application, so the application will search a translator for :featureMember
(when taking the bestuurlijkegrenzen REST api example). To make sure the right translator is selected we need to go to the right document level :Landsgrens. To achieve that action the configuration parameter :config/feature-identifier contains the hint '#xml2json/comp [first :content]'. 

## :config/feature-selector
This parameter is optional. 
Like the :config/feature-identifier this parameter can be set so the 'right' feature is selected on which the translation is performed.
Currently the only value permited is '#xml2json/comp [first :content]', this is used in the same way as the parameter :config/feature-identifier.

## :config/translators
Contains the different translator configurations that can be used during the transformation proces. Multiple translators can be given.

## Building
```lein build```

## Testing
```lein test```

## License

Copyright © 2015 PDOK

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
