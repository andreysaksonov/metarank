# Personalizing recommendations

This tutorial describes how to make personalized recommendations based on a [Ranklens](https://github.com/metarank/ranklens) dataset.
The dataset itself is a repackaged version of famous Movielens dataset with recorded real human clitkthroughts on movies.

This tutorial reproduces the system running on [demo.metarank.ai](https://demo.metarank.ai).

### Prerequisites

- [JVM 11+](https://www.oracle.com/java/technologies/downloads/) installed on your local machine
- a running [Redis](https://redis.io/download) instance
- latest [release jar file](https://github.com/metarank/metarank/releases) of Metarank

For the data input, we are using a repackaged copy of the ranklens dataset [available here](https://github.com/metarank/metarank/tree/master/src/test/resources/ranklens/events). 
The only difference with the original dataset is that we have converted it to a metarank-compatible data model with 
metadata/interaction/impression [event format](./xx_event_schema.md).

### Configuration

An example ranklens-compatible config file is available [here](https://github.com/metarank/metarank/blob/master/src/test/resources/ranklens/config.yml),
but you can write your own based on that template:
```yaml
interactions:
  - name: click
    weight: 1.0
features:
  - name: popularity
    type: number
    scope: item
    source: metadata.popularity

  - name: vote_avg
    type: number
    scope: item
    source: metadata.vote_avg

  - name: vote_cnt
    type: number
    scope: item
    source: metadata.vote_cnt

  - name: budget
    type: number
    scope: item
    source: metadata.budget

  - name: release_date
    type: number
    scope: item
    source: metadata.release_date

  - name: runtime
    type: number
    scope: item
    source: metadata.runtime

  - name: title_length
    type: word_count
    source: metadata.title
    scope: item

  - name: genre
    type: string
    scope: item
    source: metadata.genres
    values:
      - drama
      - comedy
      - thriller
      - action
      - adventure
      - romance
      - crime
      - science fiction
      - fantasy
      - family
      - horror
      - mystery
      - animation
      - history
      - music

  - name: ctr
    type: rate
    top: click
    bottom: impression
    scope: item
    bucket: 24h
    periods: [7,30]

  - name: liked_genre
    type: interacted_with
    interaction: click
    field: metadata.genres
    scope: session
    count: 10
    duration: 24h

  - name: liked_actors
    type: interacted_with
    interaction: click
    field: metadata.actors
    scope: session
    count: 10
    duration: 24h

  - name: liked_tags
    type: interacted_with
    interaction: click
    field: metadata.tags
    scope: session
    count: 10
    duration: 24h

  - name: liked_director
    type: interacted_with
    interaction: click
    field: metadata.director
    scope: session
    count: 10
    duration: 24h
```

### 1. Data Bootstraping

The bootstrap job will process your incoming events based on a config file and produce a couple of output parts:
1. `dataset` - backend-agnostic numerical feature values for all the clickhroughs in the dataset
2. `features` - snapshot of the latest feature values, which should be used in the inference phase later
3. `savepoint` - an Apache Flink savepoint to seamlessly continue processing online events after the bootstrap job

Run the following command with Metarank CLI and provide the [`events.json.gz`](https://github.com/metarank/metarank/tree/master/src/test/resources/ranklens/events) and `config.yml` files locations as it's parameters:

```shell
java -cp metarank.jar ai.metarank.mode.bootstrap.Bootstrap \
  --events <dir with events.json.gz> \
  --out <output directory> \
  --config <path to config.yml>
```



### 2. Training the Machine Learning model

When the Bootstrap job is finished, you can train the model using the `config.yml` and the output of the Bootstrap job. 
The Training job will parse the input data, do the actual training and produce the model file:

```shell
java -cp metarank.jar ai.metarank.mode.train.Train \
  --input <bootstrap output directory>/dataset \
  --config <path to config.yml> \
  --model-type lambdamart-lightgbm \
  --model-file <output model file> 
```

### 3. Upload

Metarank is using Redis for inference (real-time data processing for online personalization), so you need to load 
the current versions of feature values there after the Bootstrap job. Use your Redis instance url as the `host` parameter:

```shell
java -cp metarank.jar ai.metarank.mode.upload.Upload \
  --features-dir <bootstrap output directory>/features \
  --host localhost \
  --format json
```

### 4. Inference

Run Metarank REST API service to process feedback events and re-rank in real-time. 
By default Metarank will be available on `localhost:8080` and you can send feedback events to `http://<ip>:8080/feedback` 
and get personalized ranking from `http://<ip>:8080/rank`. 
```shell
java -cp metarank.jar  ai.metarank.mode.inference.Inference \
  --config <path to config.yml>\
  --model <model file>\
  --redis-host localhost\
  --format json\
  --savepoint-dir <bootstrap output directory>/savepoint
```

## Playing with it

You can check out how we use the Metarank REST API in our [Node.js demo application](https://github.com/metarank/demo). 
Each feedback event will influence the ranking results, so the more you use the service, the better ranking you will get.