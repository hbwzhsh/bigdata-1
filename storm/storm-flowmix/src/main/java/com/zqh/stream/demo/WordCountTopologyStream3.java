package com.zqh.stream.demo;


import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * This topology demonstrates Storm's stream groupings and multilang capabilities.
 */
public class WordCountTopologyStream3 {

    public static class RandomSentenceSpout extends BaseRichSpout {
        SpoutOutputCollector collector;
        Random rand;
        String[] sentences = null;

        @Override
        public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
            this.collector = collector;
            rand = new Random();
            sentences = new String[]{ "the cow jumped over the moon", "an apple a day keeps the doctor away", "four score and seven years ago", "snow white and the seven dwarfs", "i am at two with nature" };
        }

        @Override
        public void nextTuple() {
            Utils.sleep(1000);
            String sentence = sentences[rand.nextInt(sentences.length)];
            System.out.println("\n" + sentence);
            this.collector.emit("split-stream", new Values(sentence));
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declareStream(declarer, new Fields("sentence"));
        }
        public void ack(Object id) {}
        public void fail(Object id) {}
    }

    public static class SplitSentenceBolt extends BaseRichBolt {
        private OutputCollector collector;

        @Override
        public void prepare(Map config, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            String sentence = tuple.getStringByField("sentence");
            String[] words = sentence.split(" ");
            for (String word : words) {
                this.collector.emit("count-stream", new Values(word));
            }
            this.collector.ack(tuple);
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declareStream(declarer, new Fields("word"));
        }
    }

    public static class WordCountBolt extends BaseRichBolt {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        private OutputCollector collector;
        @Override
        public void prepare(Map config, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
        }

        @Override
        public void execute(Tuple tuple) {
            String word = tuple.getString(0);
            Integer count = counts.get(word);
            if (count == null) count = 0;
            count++;
            counts.put(word, count);
            collector.emit("print-stream", new Values(word, count));
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declareStream(declarer, new Fields("word", "count"));
        }
    }

    public static class PrinterBolt extends BaseRichBolt {
        private OutputCollector collector;
        @Override
        public void prepare(Map config, TopologyContext context, OutputCollector collector) {
            this.collector = collector;
        }
        @Override
        public void execute(Tuple tuple) {
            String first = tuple.getString(0);
            int second = tuple.getInteger(1);
            System.out.println(first + "," + second);
            //collector.emit("whatever-stream", new Values(first + ":" + second));
            collector.emit(new Values(first + ":" + second));
        }

        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            //declareStream(declarer, new Fields("word:count"));
            declarer.declare(new Fields("word:count"));
        }
    }

    public static void main(String[] args) throws Exception {
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("spout", new RandomSentenceSpout(), 1);

        setBolt(builder, new SplitSentenceBolt(), "split");
        setBolt(builder, new WordCountBolt(), "count");
        setBolt(builder, new PrinterBolt(), "print");

        Config conf = new Config();
        conf.setDebug(false);
        if (args != null && args.length > 0) {
            conf.setNumWorkers(3);
            StormSubmitter.submitTopologyWithProgressBar(args[0], conf, builder.createTopology());
        } else {
            conf.setMaxTaskParallelism(3);
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("word-count", conf, builder.createTopology());
            Thread.sleep(10000);
            cluster.shutdown();
        }
    }

    public static void declareStream(OutputFieldsDeclarer declarer, Fields fields){
        declarer.declareStream("split-stream", fields);
        declarer.declareStream("count-stream", fields);
        declarer.declareStream("print-stream", fields);
        //declarer.declareStream("whatever-stream", fields);      //⬅
    }

    public static void setBolt(TopologyBuilder builder, IRichBolt bolt, String name){
        builder.setBolt(name, bolt, 2)
                .shuffleGrouping("spout", name + "-stream")
                .fieldsGrouping("split", name + "-stream", new Fields("word"))
                .shuffleGrouping("count", name + "-stream")
                .shuffleGrouping("print", name + "-stream")     //⬅
        ;
    }

}