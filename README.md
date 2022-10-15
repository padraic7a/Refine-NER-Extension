
# OpenRefine Named-Entity Recognition
This extension adds support for [named-entity recognition](http://en.wikipedia.org/wiki/Named-entity_recognition) services to [Google Refine](http://code.google.com/p/google-refine/) / [OpenRefine](https://github.com/OpenRefine/OpenRefine).

![[Screenshot of the extension]](http://freeyourmetadata.org/images/ner-extension.png)

## Installation

1. Download the zip file from the latest [release](https://github.com/stkenny/Refine-NER-Extension/releases/download/v1.6.2/ner-1.6.2.zip)
1. If it does not exist, create a folder named **extensions/ner** under your user workspace directory for OpenRefine. The workspace should be located in the following places depending on your operating system (see [OpenRefine FAQ](https://github.com/OpenRefine/OpenRefine/wiki/FAQ-Where-Is-Data-Stored) for more details):
    * Linux ~/.local/share/OpenRefine
    * Windows C:/Documents and Settings/<user>/Application Data/OpenRefine OR C:/Documents and Settings/<user>/Local Settings/Application Data/OpenRefine
    * Mac OSX ~/Library/Application Support/OpenRefine
2. Unzip the downloaded release into the extensions/ner folder (step 1).
3. Restart OpenRefine (OpenRefine usage instructions are provided in the [user documentation](https://github.com/OpenRefine/OpenRefine/wiki/Installation-Instructions#release-version))
4. Open or create a project
5. Click the *Named-entity recognition* button at the top right, choose *Configure services...*.

## Usage
1. Click the small triangle before the column name and choose *Extract named entities...*
2. Select the services you want to use.
3. Click *Start extraction*.

### Services

#### StanfordNLP
In order to use StanfordNLP an instance of the service must be running.

0. Download the [NLP service software](https://stanfordnlp.github.io/CoreNLP/download.html) 
0. Extract the download, and from within the extracted directory run
   ```java -mx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 15000```

#### NIF services

This option lets you connect to any annotation service which supports the [NIF protocol](https://github.com/dice-group/gerbil/wiki/How-to-create-a-NIF-based-web-service). You can find a list of services in [the configuration file of the GERBIL platform](https://github.com/dice-group/gerbil/blob/master/src/main/properties/annotators.properties) (not
all services listed there are NIF-compliant, you need to look for those with `NIFBasedAnnotatorWebservice` as a class).

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

* Execute `mvn package`
* Extension will be located into `target/`
