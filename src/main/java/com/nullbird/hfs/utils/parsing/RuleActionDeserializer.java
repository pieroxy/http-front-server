package com.nullbird.hfs.utils.parsing;

import com.google.gson.*;
import com.nullbird.hfs.config.rules.actions.*;
import com.nullbird.hfs.utils.config.RuleAction;

import java.lang.reflect.Type;

public class RuleActionDeserializer  implements JsonDeserializer<RuleAction> {
  @Override
  public RuleAction deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
    JsonObject jObject = (JsonObject) jsonElement;
    JsonElement typeObj = jObject.get("type");

    if(typeObj!= null ){
      String nodeType = typeObj.getAsString();

      switch (nodeType){
        case "AddHttpHeader":
          return jsonDeserializationContext.deserialize(jsonElement, AddHttpHeader.class);
        case "BasicAuthenticate":
          return jsonDeserializationContext.deserialize(jsonElement, BasicAuthenticate.class);
        case "HttpRedirect":
          return jsonDeserializationContext.deserialize(jsonElement, HttpRedirect.class);
        case "OverrideHost":
          return jsonDeserializationContext.deserialize(jsonElement, OverrideHost.class);
        case "ReverseProxy":
          return jsonDeserializationContext.deserialize(jsonElement, ReverseProxy.class);
        case "RespondLiteral":
          return jsonDeserializationContext.deserialize(jsonElement, RespondLiteral.class);

        default:
          throw new RuntimeException("Unknown rule action node type " + nodeType);
      }
    } else {
      throw new RuntimeException("Rule action node has no 'type' attribute");
    }
  }
}