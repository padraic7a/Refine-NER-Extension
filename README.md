
# OpenRefine Named-Entity Recognition
This extension adds support for [named-entity recognition](http://en.wikipedia.org/wiki/Named-entity_recognition) services to [Google Refine](http://code.google.com/p/google-refine/) / [OpenRefine](https://github.com/OpenRefine/OpenRefine).

![[Screenshot of the extension]](http://freeyourmetadata.org/images/ner-extension.png)

## Installation

1. Download the zip file from the latest [release v1.6](https://github.com/ernestorefinepro/Refine-NER-Extension/releases/tag/v1.6)
2. Extract the `ner-1.6.zip` into the OpenRefine folder `webapp/extensions`
3. Start or restart OpenRefine
4. Open or create a project
5. Click the *Named-entity recognition* button at the top right, choose *Configure services...* and enter your API keys.

## Usage
0. Click the small triangle before the column name and choose *Extract named entities...*
0. Select the services you want to use.
0. Click *Start extraction*.

### Services

#### StanfordNLP
In order to use StanfordNLP an instance of the service must be running.

0. Download the [NLP service software](https://stanfordnlp.github.io/CoreNLP/download.html) 
0. Extract the download, and from within the extracted directory run
   ```java -mx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 15000```

## Free Your Metadata
The Named-Entity Recognition extension has been developed as part of the [Free Your Metadata](http://freeyourmetadata.org) initiative.

## License
This extension is provided free of charge under the MIT license.

If this extension is used for research, we kindly ask that you refer to the associated paper in your publications:
<br>
van Hooland, S., De Wilde, M., Verborgh, R., Steiner, T., and Van de Walle, R.
[Exploring Entity Recognition and Disambiguation for Cultural Heritage Collections](http://freeyourmetadata.org/publications/named-entity-recognition-abstract.pdf).
Digital Scholarship in the Humanities, Vol. 30 Iss. 2, pp. 262–279, 2015.

## Compile

* Execute `mvn package assembly:single`
* Extension will be located into `target/ner-1.6.zip`
