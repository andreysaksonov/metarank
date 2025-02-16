# Metarank JSON API

Metarank can be run the API mode in order to receive events in real-time and provide personalized ranking. 
Overally there are 2 endpoints that must be integrated:
- feedback
- ranking

## Feedback

**API Endpoint**: `/feedback`

Feedback endpoint receives several types of events: metadata, interaction, ranking. 

Integrating these events is crucial for personalization to operate properly and provide relevant results. 

### Payload format
You can find events and their description on the [Supported events](xx_event_schema.md).

## Ranking

**API Endpoint**: `/rank`
**Method**: `POST`
**Querystring Parameters**:
- `explain: boolean`: used to provide extra information in the response containing calculated feature values.

Ranking endpoint does the real work of personalizing items that are passed to it. 


### Payload format

```json
{
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",// required
  "timestamp": "1599391467000",// required
  "user": "user1", // required
  "session": "session1", // required
  "fields": [ // optional
    {"name": "query", "value": "jeans"},
    {"name": "source", "value": "search"}
  ],
  "items": [ // required
    {"id": "item3", "relevancy":  2.0},
    {"id": "item1", "relevancy":  1.0},
    {"id": "item2", "relevancy":  0.5}
  ]
}
```

- `id`: a request identifier later used to join impression and interaction events. This will be the same value that you will pass to feedback endpoint for impression and ranking events.
- `user`: unique visitor identifier.
- `session`: session identifier, a single visitor may have multiple sessions.
- `fields`: an optional array of extra fields that you can use in your model, for more information refer to [Supported events](xx_event_schema.md).
- `items`: which particular items were displayed to the visitor.
- `items.id`: id of the content item. Should match the `item` property from metadata event.
- `items.relevancy`: a score which was used to rank these items. For example, it can be BM25/tfidf score coming from ElasticSearch. If your system doesn't return any relevancy score, just use `1` as a value.

### Response format

```json
{
  "items": [
    {"id": "item2", "relevancy":  2.0, "features": [{"name": "popularity", "value": 10 }]},
    {"id": "item3", "relevancy":  1.0, "features": [{"name": "popularity", "value": 5 }]},
    {"id": "item1", "relevancy":  0.5, "features": [{"name": "popularity", "value": 2 }]}
  ]
}
```

- `items.id`: id of the content item. Will `item` property from metadata event.
- `items.relevancy`: a score calculated by personalization model
- `items.features`: an array of feature values calculated by pesonaliization model. This field will be returned if `explain` field is set to `true` in the request. The structure of this object will vary depending on the feature type.
