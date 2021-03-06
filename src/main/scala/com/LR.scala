package com

import java.io.PrintWriter

import org.apache.hadoop.util.hash.Hash
import org.apache.spark.mllib.classification.{LogisticRegressionModel, LogisticRegressionWithLBFGS, LogisticRegressionWithSGD, SVMWithSGD}
import org.apache.spark.mllib.feature.Word2VecModel
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

/**
  * Created by JinBinbin on 2016/8/29.
  */
object LR {
  def main(args:Array[String]): Unit ={
    val conf = new SparkConf().setAppName("LR").setMaster("local[4]")
    val sc = new SparkContext(conf)

//    val model = LogisticRegressionModel.load(sc,"C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\modelSub\\modelSub49")
//    val weights = model.weights.toArray.zipWithIndex.sortBy(x=>x._1)
//    val out = new PrintWriter("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\weight\\weightModel49.txt")
//    out.println(model.numFeatures)
//    for ((weight,index)<-weights){
//      out.write(index+"\t"+weight+"\n")
//    }
//    out.close()

//    val data = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\15_rawQ_rawU\\train\\part-*")
//    val splits = data.randomSplit(Array(0.2,0.8),seed = 11L)
//    splits(0).saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\0.2Data")
//    splits(1).saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\0.8Data")

//    val out = new PrintWriter("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\w2vTest.txt")
//    for (vecSize<-Range(60,100,10)){
//      for (windowSize<-Range(3,7,1)){
//        val dataName = "word2vec_"+vecSize+"_"+windowSize
//        val ndcg = train_pred(sc,dataName)
//        out.println(vecSize+" "+windowSize+" "+ndcg)
//      }
//    }
//    out.close()

    val dataName = "featAll2"
    //dataProcessing(sc,0,dataName)
//    val ndcg = train_pred(sc,dataName)
    ///println(ndcg)
//    train(sc,dataName,54)
    //SVMTrain(sc,dataName,27)
    //evaluate(sc,dataName)
    statistic(sc)

    sc.stop()
  }

  def SVMTrain(sc:SparkContext,inputName:String,modelNum:Int): Unit ={
    val data = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\"+inputName+"\\train\\part-*").map({x=>
      val info = x.split("\t")
      val question = info(0)
      val user = info(1)
      val labelAndFeature = info(2).split(" ")
      val label = labelAndFeature(0).toDouble
      val indices = new Array[Int](labelAndFeature.length-1)
      val value = new Array[Double](labelAndFeature.length-1)
      for (i<-1 until labelAndFeature.length){
        val indAndVal = labelAndFeature(i).split(":")
        indices(i-1) = indAndVal(0).toInt-1
        value(i-1) = indAndVal(1).toDouble
      }
      (label,indices,value)
    })
    val featureNum = data.flatMap(x=>x._2).max()+1
    val training = data.map(x=>LabeledPoint(x._1,Vectors.sparse(featureNum,x._2,x._3))).cache()

    val test = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\"+inputName+"\\test\\part-*").map({x=>
      val info = x.split("\t")
      val question = info(0)
      val user = info(1)
      val feature = info(2).split(" ")
      val indices = new Array[Int](feature.length)
      val value = new Array[Double](feature.length)
      for (i<-0 until feature.length){
        val indAndVal = feature(i).split(":")
        indices(i) = indAndVal(0).toInt-1
        value(i) = indAndVal(1).toDouble
      }
      (question,user,Vectors.sparse(featureNum,indices,value))
    }).cache()

    val numIterations = 1000
    val model = SVMWithSGD.train(training, numIterations)
    model.clearThreshold()

    // Compute raw scores on the test set.
    val scoreAndLabels = test.map { x =>
      val score = model.predict(x._3)
      x._1+","+x._2+","+score
    }.repartition(1)
      .saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\resSub"+modelNum)
  }

  def train_pred(sc:SparkContext,inputName:String): Double ={
    val data = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\"+inputName+"\\train\\part-*").map({x=>
      val info = x.split("\t")
      val question = info(0)
      val user = info(1)
      val labelAndFeature = info(2).split(" ")
      val label = labelAndFeature(0).toDouble
      val indices = new Array[Int](labelAndFeature.length-1)
      val value = new Array[Double](labelAndFeature.length-1)
      for (i<-1 until labelAndFeature.length){
        val indAndVal = labelAndFeature(i).split(":")
        indices(i-1) = indAndVal(0).toInt-1
        value(i-1) = indAndVal(1).toDouble
      }
      (label,indices,value,question,user)
    })
    val featureNum = data.flatMap(x=>x._2).max()+1
    val training = data.map(x=>(x._4,x._5,LabeledPoint(x._1,Vectors.sparse(featureNum,x._2,x._3)))).cache()

    val splits = training.randomSplit(Array(0.9,0.1),seed = 11L)
    val training2 = splits(0).map(x=>x._3).cache()
    val test2 = splits(1).cache()

    val model = new LogisticRegressionWithLBFGS().setNumClasses(2).run(training2)

    val preAndLabel = test2
      .map{case (qid,uid,LabeledPoint(label,feature))=>
        val pre = model.predict(feature)
        (qid,uid,pre,label.toInt)
      }

    val ndcg = NDCG.NDCG(preAndLabel)
//    val pre = evaluate(preAndLabel)
//    pre
    ndcg
//      .repartition(1)
//      .saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\localtest\\res"+inputName)

//    evaluate(sc,inputName)
//    val predictionAndLabel = test2
//      .map({case (qid,uid,LabeledPoint(label,feature))=>
//        val prediction = model.clearThreshold().predict(feature)
//        (qid,uid,prediction,label,Math.abs(label-prediction))
//      })
//      .filter(x=>x._4==1.0)
//      .sortBy(x=>x._5)
//
//    predictionAndLabel.map(x=>x._1+"\t"+x._2+"\t"+x._3+"\t"+x._4+"\t"+x._5).repartition(1).saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\res0.8_df5_noraw")
  }

  def train(sc:SparkContext,inputName:String,modelNum:Int): Unit ={
    val data = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\"+inputName+"\\train\\part-*").map({x=>
      val info = x.split("\t")
      val question = info(0)
      val user = info(1)
      val labelAndFeature = info(2).split(" ")
      val label = labelAndFeature(0).toDouble
      val indices = new Array[Int](labelAndFeature.length-1)
      val value = new Array[Double](labelAndFeature.length-1)
      for (i<-1 until labelAndFeature.length){
        val indAndVal = labelAndFeature(i).split(":")
        indices(i-1) = indAndVal(0).toInt-1
        value(i-1) = indAndVal(1).toDouble
      }
      (label,indices,value,question,user)
    })
    val featureNum = data.flatMap(x=>x._2).max()+1
    val training = data.map(x=>(x._4,x._5,LabeledPoint(x._1,Vectors.sparse(featureNum,x._2,x._3)))).cache()

    val test = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\"+inputName+"\\test\\part-*").map({x=>
      val info = x.split("\t")
      val question = info(0)
      val user = info(1)
      val feature = info(2).split(" ")
      val indices = new Array[Int](feature.length)
      val value = new Array[Double](feature.length)
      for (i<-0 until feature.length){
        val indAndVal = feature(i).split(":")
        indices(i) = indAndVal(0).toInt-1
        value(i) = indAndVal(1).toDouble
      }
      (question,user,Vectors.sparse(featureNum,indices,value))
    }).cache()

    val model = new LogisticRegressionWithLBFGS().setNumClasses(2).run(training.map(x=>x._3))
    model.save(sc,"C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\modelSub\\modelSub"+modelNum)
    val model2 = LogisticRegressionModel.load(sc,"C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\modelSub\\modelSub"+modelNum).clearThreshold()
    val predictionAndLabel = test
      .map({x=>
        val prediction = model2.predict(x._3).toFloat
        x._1+","+x._2+","+prediction
      })
      .repartition(1)
      .saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\resSub\\resSub"+modelNum)
  }

  def dataProcessing(sc:SparkContext,minCount:Int,outputName:String):Unit = {
    val userInfo = sc
      .textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\user_info.txt")
      .map({x=>
        val info = x.split("\t")
        val user = info(0)
        val tags = info(1).split("/").map(_.toInt)
        val term = info(2).split("/").map(_.toInt)
        val word = if (info.length == 4){
          info(3).split("/").map(_.toInt)
        }else{
          new Array[Int](0)
        }
        (user,(tags,term,word))
      })

    val questionInfo = sc
      .textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\question_info.txt")
      .map({x=>
        val info = x.split("\t")
        val question = info(0)
        val tags = info(1).split("/").map(_.toInt)
        val term = if (info(2)!=""){
          info(2).split("/").map(_.toInt)
        }else{
          new Array[Int](0)
        }
        val word = if (info(3)!=""){
          info(3).split("/").map(_.toInt)
        }else{
          new Array[Int](0)
        }
        val support = info(4).toInt
        val answer = info(5).toInt
        val goodAnswer = info(6).toInt
        (question,(tags,term,word,support,answer,goodAnswer))
      })

    /*对专家标间进行简单分类*/
//    val userLabelTag = userInfo.map({x=>
//      x._2._1
//    }).collect()
//    val listRes = ListBuffer.empty[Set[Int]]
//    userLabelTag.foreach({ x =>
//      val set = x.toSet
//      val newSet = new scala.collection.mutable.HashSet[Int]()
//      val listTmp = listRes.toList
//      listRes.clear()
//      for (s<-listTmp){
//        if (s.intersect(set).nonEmpty){
//          newSet ++= s
//        }else{
//          listRes += s
//        }
//      }
//      newSet ++= set
//      listRes += newSet.toSet
//    })
//    var maxLabel = 0
//    for (set<-listRes){
//      if (set.max>maxLabel)
//        maxLabel = set.max
//    }
//    val labelToClass = Array.fill[Int](maxLabel+1)(-1)
//    for (i<-listRes.indices){
//      for (label<-listRes(i)){
//        labelToClass(label) = i+1
//      }
//    }

    val userInfoMap = userInfo.collect().toMap
    val (supportCB,answerCB,goodAnswerCB) = getCutBorder(questionInfo)
    val questionInfoMap = questionInfo.map({x=>
      val support = x._2._4
      val answer = x._2._5
      val goodAnswer = x._2._6
      var k1 = 0
      while (support>supportCB(k1)._2) k1 = k1 + 1
      var k2 = 0
      while (answer>answerCB(k2)._2) k2 = k2 + 1
      var k3 = 0
      while (goodAnswer>goodAnswerCB(k3)._2) k3 = k3 + 1
      (x._1,(x._2._1,x._2._2,x._2._3,k1,k2,k3,Array[Double](support,answer,goodAnswer)))
    }).collect().toMap

    val invitedInfo = sc
      .textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\label2.txt")
      .map({x=>
        val info = x.split("\t")
        val question = info(0)
        val user = info(1)
        val label = info(2).toInt
        val gbdtFeature = info(3)
        (question,user,label,gbdtFeature)
      }).cache()

    /*统计专家回答率，问题被回答率*/
    val userAnswerRate = invitedInfo.map(x=>(x._2,(x._3,1))).reduceByKey((x,y)=>(x._1+y._1,x._2+y._2))/*.map(x=>(x._1,x._2._1*1.0/x._2._2))*/.collect().toMap
    val questionAnsweredRate = invitedInfo.map(x=>(x._1,(x._3,1))).reduceByKey((x,y)=>(x._1+y._1,x._2+y._2))/*.map(x=>(x._1,x._2._1*1.0/x._2._2))*/.collect().toMap

    /*统计用户回答问题的热门程度,用户回答不同标签问题的数量*/
    val userPreferMap = new mutable.HashMap[String,(Array[Double],Array[Double],Array[Double],Array[Double],Array[Double])]()
    val userPrefer = invitedInfo.map({x=>
      val question = x._1
      val user = x._2
      val label = x._3
      val questionInfo = questionInfoMap.getOrElse(question,null)
      (user,(questionInfo._4,questionInfo._5,questionInfo._6,questionInfo._1(0),label))
    }).collect()
    for ((user,(supprot,answer,goodAnswer,qlabel,label))<-userPrefer){
      val info = userPreferMap.getOrElse(user,Tuple5(new Array[Double](10),new Array[Double](10),new Array[Double](10),new Array[Double](20),new Array[Double](20)))
      if (label == 1) {
        info._1(supprot) = info._1(supprot) + 1
        info._2(answer) = info._2(answer) + 1
        info._3(goodAnswer) = info._3(answer) + 1
        info._4(qlabel) = info._4(qlabel) + 1
      }
      info._5(qlabel) = info._5(qlabel) + 1
      userPreferMap.put(user,info)
    }

    /*统计问题被回答的专家的标签*/
    val questionPreferedMap = new mutable.HashMap[String,(Array[Double],Array[Double])]()
    val questionPrefer = invitedInfo.map{x=>
      val question = x._1
      val user = x._2
      val label = x._3
      val userInfo = userInfoMap.getOrElse(user,null)
      (question,userInfo._1,label)
    }.collect()
    for ((question,userid,label)<-questionPrefer){
      val info = questionPreferedMap.getOrElse(question,(new Array[Double](143),new Array[Double](143)))
      for (uLabel<-userid){
        if (label == 1) {
          info._1(uLabel) = info._1(uLabel) + 1
        }
        info._2(uLabel) = info._2(uLabel) + 1
      }
      questionPreferedMap.put(question,info)
    }


    /*提取原始特征（字、词、标签）*/
    val questionFeatureMap = invitedInfo
      .flatMap({x=>
        val question = x._1
        val questionInfo = questionInfoMap.getOrElse(question,null)
        (questionInfo._1++questionInfo._2++questionInfo._3).distinct
      })
      .map(x=>(x,1))
      .reduceByKey(_+_)
      .filter(_._2 > minCount)
      .map(_._1)
      .zipWithIndex()
      .map(x=>(x._1,x._2.toInt))
      .collect()
      .toMap
    val userFeatureMap = invitedInfo
      .flatMap({x=>
        val user = x._2
        val userInfo = userInfoMap.getOrElse(user,null)
        (userInfo._1++userInfo._2++userInfo._3).distinct
      })
      .map(x=>(x,1))
      .reduceByKey(_+_)
      .filter(_._2 > minCount)
      .map(_._1)
      .zipWithIndex()
      .map(x=>(x._1,x._2.toInt))
      .collect()
      .toMap
    val questionFeatureNum = questionFeatureMap.size
    val userFeatureNum = userFeatureMap.size

//    val questionFeatureMap = questionInfo
//      .flatMap({x=>
//        (x._2._1++x._2._2++x._2._3).distinct
//      })
//      .map(x=>(x,1))
//      .reduceByKey(_+_)
//      .filter(_._2 > minCount)
//      .map(_._1)
//      .zipWithIndex()
//      .map(x=>(x._1,x._2.toInt))
//      .collect()
//      .toMap
//    val userFeatureMap = userInfo.flatMap({x=>
//      (x._2._1++x._2._2++x._2._3).distinct
//    })
//      .map(x=>(x,1))
//      .reduceByKey(_+_)
//      .sortBy(x=>x._2)
//      .filter(_._2 > minCount)
//      .map(_._1)
//      .zipWithIndex()
//      .map(x=>(x._1,x._2.toInt))
//      .collect()
//      .toMap
//
//    val questionFeatureNum = questionFeatureMap.size
//    val userFeatureNum = userFeatureMap.size

    /*提取由gbdt做出来的特征*/
//    val gbdtFeature = invitedInfo.map({x=>
//      val s = x._4.replaceAll(",","").split(" ").map(_.toInt)
//      s
//    })
//    val gbdtFeatureMap = gbdtFeature
//      .map(x=>x.map(x=>Set(x)))
//      .reduce({(x,y)=>
//        val combine = for (i<-x.indices) yield {
//          x(i)++y(i)
//        }
//        combine.toArray
//      })
//      .map(_.toArray.zipWithIndex.toMap)
//    var gbdtFeatureNum = 0
//    for (map<-gbdtFeatureMap){
//      gbdtFeatureNum = gbdtFeatureNum + map.size
//    }

      /*计算所有专家和问题记录（包括有label和没label的数据）的IDF*/
//    val testQAU = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\nolabel.txt").map({x=>
//      val sp = x.split("\t")
//      val info = sp(0).split(",")
//      val question = info(0)
//      val user = info(1)
//      (question,user)
//    })
//    val questionAndUser =invitedInfo
//      .map(x=>(x._1,x._2))
//      .union(testQAU)
//    val recordNum = questionAndUser.count()
//    val DF = questionAndUser
//      .flatMap({x=>
//        val question = x._1
//        val user = x._2
//        val questionInfo = questionInfoMap.getOrElse(question,null)
//        val userInfo = userInfoMap.getOrElse(user,null)
//        val tags = questionInfo._1++questionInfo._2++questionInfo._3++userInfo._1++userInfo._2++userInfo._3
//        tags.distinct
//      })
//      .map(x=>(x,1))
//      .reduceByKey(_+_)
//      .collect()
//    val IDFData = new Array[Double](rawFeatureNum)
//    for ((feature,count)<-DF){
//      val ind = featureMap.getOrElse(feature,-1)
//      if (ind != -1) {
//        IDFData(ind) = Math.log10(recordNum * 1.0 / count)
//      }
//    }

    /*word2Vec*/
    val model = Word2VecModel.load(sc, "C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\word2Vec_10_50_4")
    val word2vecMap = model.getVectors

    /*提取没label的专家-问题记录的特征*/
    val testOnline = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\nolabel2.txt").map({x=>
      val sp = x.split("\t")
      val info = sp(0).split(",")
      val question = info(0)
      val user = info(1)
      val questionInfo = questionInfoMap.getOrElse(question,null)
      val userInfo = userInfoMap.getOrElse(user,null)
      val rel = new Array[Double](3)
      rel(0) = questionInfo._1.intersect(userInfo._1).length
      rel(1) = questionInfo._2.intersect(userInfo._2).length
      rel(2) = questionInfo._3.intersect(userInfo._3).length
      val hot = userPreferMap.getOrElse(user,Tuple5(new Array[Double](10),new Array[Double](10),new Array[Double](10),new Array[Double](20),new Array[Double](20)))
      //val uPreferQLabelRate = hot._4.zip(hot._5).map(x=>if (x._2 != 0) {x._1 * 1.0 / x._2} else {0})
      val hot2 = questionPreferedMap.getOrElse(question,(new Array[Double](143),new Array[Double](143)))
      //val qPreferedULabelRate = hot2._1.zip(hot2._2).map(x=>if (x._2!=0) {x._1 * 1.0 / x._2} else {0})
      //val gbdtFeatureInd = sp(1).replaceAll(",","").split(" ").map(_.toInt)
      val rawQuestionFeature = getRawFeature2(question,null,questionInfoMap,questionAnsweredRate,userInfoMap,userAnswerRate,questionFeatureMap,questionFeatureNum,word2vecMap)
      val rawUserFeature = getRawFeature2(null,user,questionInfoMap,questionAnsweredRate,userInfoMap,userAnswerRate,userFeatureMap,userFeatureNum,word2vecMap)
      //val gbdtFeature = getGBDTFeature(gbdtFeatureInd,gbdtFeatureMap,gbdtFeatureNum)
      (question,user,Vectors.dense(rawQuestionFeature++rawUserFeature++rel++hot._1++hot._2++hot._3++hot._4++hot2._1))
    })

    /*提取有label的专家-问题记录的特征*/
    val data = invitedInfo.map({x=>
      val question = x._1
      val user = x._2
      val label = x._3
      val questionInfo = questionInfoMap.getOrElse(question,null)
      val userInfo = userInfoMap.getOrElse(user,null)
      val rel = new Array[Double](3)
      rel(0) = questionInfo._1.intersect(userInfo._1).length
      rel(1) = questionInfo._2.intersect(userInfo._2).length
      rel(2) = questionInfo._3.intersect(userInfo._3).length
      val hot = userPreferMap.getOrElse(user,Tuple5(new Array[Double](10),new Array[Double](10),new Array[Double](10),new Array[Double](20),new Array[Double](20)))
      //val uPreferQLabelRate = hot._4.zip(hot._5).map(x=>if (x._2 != 0) {x._1 * 1.0 / x._2} else {0})
      val hot2 = questionPreferedMap.getOrElse(question,(new Array[Double](143),new Array[Double](143)))
      //val qPreferedULabelRate = hot2._1.zip(hot2._2).map(x=>if (x._2!=0) {x._1 * 1.0 / x._2} else {0})
      //val gbdtFeatureInd = x._4.replaceAll(",","").split(" ").map(_.toInt)
      val rawQuestionFeature = getRawFeature2(question,null,questionInfoMap,questionAnsweredRate,userInfoMap,userAnswerRate,questionFeatureMap,questionFeatureNum,word2vecMap)
      val rawUserFeature = getRawFeature2(null,user,questionInfoMap,questionAnsweredRate,userInfoMap,userAnswerRate,userFeatureMap,userFeatureNum,word2vecMap)
      //val gbdtFeature = getGBDTFeature(gbdtFeatureInd,gbdtFeatureMap,gbdtFeatureNum)
      (question,user,LabeledPoint(label.toDouble,Vectors.dense(rawQuestionFeature++rawUserFeature++rel++hot._1++hot._2++hot._3++hot._4++hot2._1)))
    })

    data.map(x=>{
      val qid = x._1
      val uid = x._2
      val label = x._3.label
      val feature = x._3.features.toSparse
      val indices = feature.indices.map(x=>x+1)
      val value = feature.values
      val feature2 = indices.zip(value).map(x=>x._1+":"+x._2)
      qid+"\t"+uid+"\t"+label+" "+feature2.mkString(" ")
    }).saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\"+outputName+"\\train")
    testOnline.map(x=>{
      val qid = x._1
      val uid = x._2
      val feature = x._3.toSparse
      val indices = feature.indices.map(x=>x+1)
      val value = feature.values
      val feature2 = indices.zip(value).map(x=>x._1+":"+x._2)
      qid+"\t"+uid+"\t"+feature2.mkString(" ")
    }).saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\libSVM\\"+outputName+"\\test")
    println(data.first()._3.features.size)
  }

  def getRawFeature2(question:String,
                    user:String,
                    questionInfoMap:Map[String,(Array[Int],Array[Int],Array[Int],Int,Int,Int,Array[Double])],
                    questionAnsweredRate:Map[String,(Int,Int)],
                    userInfoMap:Map[String,(Array[Int],Array[Int],Array[Int])],
                    userAnswerRate:Map[String,(Int,Int)],
                    featureMap:Map[Int,Int],
                    rawFeatureNum:Int,
                     word2VecMap:Map[String,Array[Float]]): Array[Double] ={
    val rawFeature = new Array[Double](rawFeatureNum)
    val questionInfo = questionInfoMap.getOrElse(question,null)
    val userInfo = userInfoMap.getOrElse(user,null)
    val tags = if (user==null){
      questionInfo._1++questionInfo._2++questionInfo._3
    }else{
      userInfo._1++userInfo._2++userInfo._3
    }
    val vecSize = word2VecMap("0").length
    val word2Vec = new Array[Double](vecSize)
    for (tag<-tags){
      val ind = featureMap.getOrElse(tag,-1)
      if (ind != -1) {
        rawFeature(ind) = rawFeature(ind) + 1
      }
      val vec = word2VecMap(tag.toString)
      for (i<-0 until vecSize){
        word2Vec(i) = word2Vec(i) + vec(i)
      }
    }
    val specialQuestion = new Array[Double](9)
    val specialUser = new Array[Double](6)
    if (user == null) {
      specialQuestion(0) = questionInfo._4
      specialQuestion(1) = questionInfo._5
      specialQuestion(2) = questionInfo._6
      specialQuestion(3) = questionInfo._1.length
      specialQuestion(4) = questionInfo._2.length
      specialQuestion(5) = questionInfo._3.length
      specialQuestion(6) = questionAnsweredRate.getOrElse(question,Tuple2(0,0))._1
      specialQuestion(7) = questionAnsweredRate.getOrElse(question,Tuple2(0,0))._2
      if (specialQuestion(7)!=0){
        specialQuestion(8) = specialQuestion(6) * 1.0 / specialQuestion(7)
      }
    }else {
      specialUser(0) = userInfo._1.length
      specialUser(1) = userInfo._2.length
      specialUser(2) = userInfo._3.length
      specialUser(3) = userAnswerRate.getOrElse(user, Tuple2(0, 0))._1
      specialUser(4) = userAnswerRate.getOrElse(user, Tuple2(0, 0))._2
      if (specialUser(4) != 0) {
        specialUser(5) = specialUser(3) * 1.0 / specialUser(4)
      }
    }
    if (user==null){
      /*rawFeature++*/specialQuestion++questionInfo._7++word2Vec
    }else{
      /*rawFeature++*/specialUser++word2Vec
    }
  }

  def getGBDTFeature(gbdtFeatureInd:Array[Int],
                     gbdtFeatureMap:Array[Map[Int,Int]],
                     gbdtFeatureNum:Int): Array[Double] ={
    val gbdtFeature = new Array[Double](gbdtFeatureNum)
    var index = 0
    for (i <- gbdtFeatureInd.indices){
      val featureInd = gbdtFeatureInd(i)
      val ind = gbdtFeatureMap(i).get(featureInd).get
      gbdtFeature(index+ind) = 1
      index = index + gbdtFeatureMap(i).size
    }
    gbdtFeature
  }

  def getCutBorder(questionInfo:RDD[(String,(Array[Int],Array[Int],Array[Int],Int,Int,Int))]): (Array[(Int,Int)],Array[(Int,Int)],Array[(Int,Int)]) ={
    val support = borderAnalyse(questionInfo,4)
    val answer = borderAnalyse(questionInfo,5)
    val goodAnswer = borderAnalyse(questionInfo,6)
    (support,answer,goodAnswer)
  }

  def borderAnalyse(questionInfo:RDD[(String,(Array[Int],Array[Int],Array[Int],Int,Int,Int))],no:Int):Array[(Int,Int)] = {
    val splitsNum = 10
    val cutInfo = new Array[(Int,Int)](splitsNum)
    val questionNum = questionInfo.count()
    val attAndCount = no match {
      case 4 => questionInfo.map(x => (x._2._4, 1)).reduceByKey(_ + _).sortByKey().collect()
      case 5 => questionInfo.map(x => (x._2._5, 1)).reduceByKey(_ + _).sortByKey().collect()
      case 6 => questionInfo.map(x => (x._2._6, 1)).reduceByKey(_ + _).sortByKey().collect()
    }
    var k = 0
    var sum = 0
    var min = 0
    var max = 0
    for (i<-0 until attAndCount.length){
      val support = attAndCount(i)._1
      val count = attAndCount(i)._2
      if (sum < questionNum/splitsNum){
        if (sum == 0){
          min = support
        }
        sum = sum + count
      }else{
        max = support - 1
        cutInfo(k) = (min,max)
        min = support
        sum = count
        k = k + 1
      }
    }
    max = attAndCount(attAndCount.length-1)._1
    cutInfo(k) = (min,max)
    cutInfo
  }

  def evaluate(data:RDD[(String,String,Double,Int)]): Double ={
    val input = data.map{x=>
      val pre = if (x._3>=0.5) {1} else{0}
      (pre,x._4)
    }
    val total = input.count()
    val cor = input.filter(x=>x._1==x._2).count()
    val precision = cor*1.0/total
    precision
//    val precision = input.filter(x=>x._1==1).count()
//    val recall = input.filter(x=>x._2==1).count()
//    val correct = input.filter(x=>x._2==1&& x._1==x._2).count()
//    println(precision+" "+recall+" "+correct)
  }

  def statistic(sc:SparkContext): Unit ={
    val label = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\data\\invited_info_train.txt").map{x=>
      val info = x.split("\t")
      val qid = info(0)
      val uid = info(1)
      val target = info(2).toInt
      ((qid,uid),target)
    }.reduceByKey(_+_)

    val nolabel = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\data\\validate_nolabel.txt").map{x=>
      val info = x.split(",")
      val qid = info(0)
      val uid = info(1)
      ((qid,uid),1.0)
    }

    val score = sc.textFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\data\\merge.csv").map{x=>
      val info = x.split(",")
      val qid = info(0)
      val uid = info(1)
      val score = info(2).toDouble
      ((qid,uid),score)
    }

    val prst = label.join(nolabel).map(x=>((x._1._1,x._1._2),x._2._1)).filter(x=>x._2==1)
    score.leftOuterJoin(prst).map{x=>
      val qid = x._1._1
      val uid = x._1._2
      val score = if (x._2._2 == None){x._2._1}else {0.0}
      qid+","+uid+","+score
    }.repartition(1).saveAsTextFile("C:\\Users\\zjcxj\\Desktop\\2016ByteCup\\data\\merge_aft")

  }
}
