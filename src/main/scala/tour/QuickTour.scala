/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tour

import com.mongodb.ConnectionString

import scala.collection.immutable.IndexedSeq
import org.mongodb.scala._
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._

import tour.Helpers._
import org.mongodb.scala.connection.NettyStreamFactoryFactory
import scala.collection.JavaConverters._
import io.netty.channel.EventLoopGroup

/**
  * The QuickTour code example
  */
object QuickTour {
  //scalastyle:off method.length

  /**
    * Run this main method to see the output of this quick example.
    *
    * @param args takes an optional single argument for the connection string
    * @throws Throwable if an operation fails
    */
  def main(args: Array[String]): Unit = {

    //TODO: Update the connection string
    val mongoClientSettings: MongoClientSettings = MongoClientSettings.builder()
      .applyConnectionString(new ConnectionString(args.head))
      .streamFactoryFactory(NettyStreamFactoryFactory())
      .applyToSslSettings(b => b.enabled(true))
      .build()

    val mongoClient: MongoClient = if (args.isEmpty) MongoClient() else MongoClient(mongoClientSettings)
    val database: MongoDatabase = mongoClient.getDatabase("people")
    val collection: MongoCollection[Document] = database.getCollection("person")
    collection.drop().results()

    // make a document and insert it
    val doc: Document = Document("_id" -> 0, "name" -> "MongoDB", "type" -> "database",
      "count" -> 1, "info" -> Document("x" -> 203, "y" -> 102))

    collection.insertOne(doc).results()
    collection.find.first().printResults()

    // now, lets add lots of little documents to the collection so we can explore queries and cursors
    val documents: IndexedSeq[Document] = (1 to 100) map { i: Int => Document("i" -> i) }
    val insertObservable = collection.insertMany(documents)

    val insertAndCount = for {
      insertResult <- insertObservable
      countResult <- collection.countDocuments()
    } yield countResult

    println(s"total # of documents after inserting 100 small ones (should be 101):  ${insertAndCount.headResult()}")
    collection.find().first().printHeadResult()

    // Query Filters
    collection.find(equal("i", 71)).first().printHeadResult()
    collection.find(gt("i", 50)).printResults()
    collection.find(and(gt("i", 50), lte("i", 100))).printResults()

    // Sorting
    collection.find(exists("i")).sort(descending("i")).first().printHeadResult()

    // Projection
    collection.find().projection(excludeId()).first().printHeadResult()

    //Aggregation
    collection.aggregate(Seq(
      filter(gt("i", 0)),
      project(Document("""{ITimes10: {$multiply: ["$i", 10]}}"""))
    )).printResults()

    // Update One
    collection.updateOne(equal("i", 10), set("i", 110)).printHeadResult("Update Result: ")

    // Update Many
    collection.updateMany(lt("i", 100), inc("i", 100)).printHeadResult("Update Result: ")

    // Delete One
    collection.deleteOne(equal("i", 110)).printHeadResult("Delete Result: ")

    // Delete Many
    collection.deleteMany(gte("i", 100)).printHeadResult("Delete Result: ")
    collection.drop().results()

    // ordered bulk writes
    val writes: List[WriteModel[_ <: Document]] = List(
      InsertOneModel(Document("_id" -> 4)),
      InsertOneModel(Document("_id" -> 5)),
      InsertOneModel(Document("_id" -> 6)),
      UpdateOneModel(Document("_id" -> 1), set("x", 2)),
      DeleteOneModel(Document("_id" -> 2)),
      ReplaceOneModel(Document("_id" -> 3), Document("_id" -> 3, "x" -> 4))
    )

    collection.bulkWrite(writes).printHeadResult("Bulk write results: ")

    collection.drop().results()

    collection.bulkWrite(writes, BulkWriteOptions().ordered(false)).printHeadResult("Bulk write results (unordered): ")

    collection.find().printResults("Documents in collection: ")

    // Clean up
    collection.drop().results()

    // release resources
    mongoClient.close()
  }

}
