package com.redislabs.redisai;

import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.util.SafeEncoder;

public class Dag implements DagRunCommands<Dag> {
  private final List<List<byte[]>> commands = new ArrayList<>();
  private final List<Boolean> tensorgetflag = new ArrayList<>();

  /** Direct acyclic graph of operations to run within RedisAI */
  public Dag() {}

  protected List<?> processDagReply(List<?> reply) {
    List<Object> outputList = new ArrayList<>(reply.size());
    for (int i = 0; i < reply.size(); i++) {
      if (this.tensorgetflag.get(i)) {
        outputList.add(Tensor.createTensorFromRespReply((List<?>) reply.get(i)));
      } else {
        outputList.add(reply.get(i));
      }
    }
    return outputList;
  }

  @Override
  public Dag setTensor(String key, Tensor tensor) {
    List<byte[]> args = tensor.tensorSetFlatArgs(key, true);
    this.commands.add(args);
    this.tensorgetflag.add(false);
    return this;
  }

  @Override
  public Dag getTensor(String key) {
    List<byte[]> args = Tensor.tensorGetFlatArgs(key, true);
    this.commands.add(args);
    this.tensorgetflag.add(true);
    return this;
  }

  @Override
  public Dag runModel(String key, String[] inputs, String[] outputs) {
    List<byte[]> args = Model.modelRunFlatArgs(key, inputs, outputs, true);
    this.commands.add(args);
    this.tensorgetflag.add(false);
    return this;
  }

  @Override
  public Dag runScript(String key, String function, String[] inputs, String[] outputs) {
    List<byte[]> args = Script.scriptRunFlatArgs(key, function, inputs, outputs, true);
    this.commands.add(args);
    this.tensorgetflag.add(false);
    return this;
  }

  List<byte[]> dagRunFlatArgs(String[] loadKeys, String[] persistKeys) {
    List<byte[]> args = new ArrayList<>();
    if (loadKeys != null && loadKeys.length > 0) {
      args.add(Keyword.LOAD.getRaw());
      args.add(SafeEncoder.encode(String.valueOf(loadKeys.length)));
      for (String key : loadKeys) {
        args.add(SafeEncoder.encode(key));
      }
    }
    if (persistKeys != null && persistKeys.length > 0) {
      args.add(Keyword.PERSIST.getRaw());
      args.add(SafeEncoder.encode(String.valueOf(persistKeys.length)));
      for (String key : persistKeys) {
        args.add(SafeEncoder.encode(key));
      }
    }
    for (List<byte[]> command : this.commands) {
      args.add(Keyword.PIPE.getRaw());
      args.addAll(command);
    }
    return args;
  }
}
