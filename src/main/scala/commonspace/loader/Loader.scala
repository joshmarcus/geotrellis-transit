package commonspace.loader

import commonspace._
import commonspace.graph._
import commonspace.index._

import commonspace.loader.gtfs.GtfsFiles
import commonspace.loader.osm.OsmFileSet

import scala.collection.mutable

import java.io._

object Loader {
  def write[T](path:String,o:T) = {
    val file = new FileOutputStream(path)
    val buffer = new BufferedOutputStream(file)
    val output = new ObjectOutputStream(buffer)
    try {
      output.writeObject(o)
    } catch {
      case e:Exception =>
        val f = new File(path)
        if(f.exists) { f.delete }
        throw e
    } finally{
      output.close()
    }
    Logger.log(s"Wrote graph to $path")
  }

  def buildGraph(config:GraphConfiguration,fileSets:Iterable[GraphFileSet]) = {
    val context = buildContext(fileSets.toSeq)
    write(new File(config.dataDirectory,"walking.graph").getPath, context.graph)
    write(new File(config.dataDirectory,"vertex.info").getPath, context.namedLocations)
    write(new File(config.dataDirectory,"edge.info").getPath, context.namedWays)
    Logger.log(s"Wrote graph data to ${config.dataDirectory}")
    context
  }

  def loadFileSet(fileSet:GraphFileSet):ParseResult = {
    Logger.timedCreate(s"Loading ${fileSet.name} data into unpacked graph...",
                        "Upacked graph created.") { () =>
      fileSet.parse
    }
  }

  def buildContext(fileSets:Seq[GraphFileSet]):GraphContext = {
    if(fileSets.length < 1) { sys.error("Argument error: Empty list of file sets.") }

    // Merge the graphs from all the File Sets into eachother.
    val mergedResult = 
      fileSets.drop(1)
              .foldLeft(loadFileSet(fileSets(0))) { (result,fileSet) =>
                 result.merge(loadFileSet(fileSet))
               }

    val index =     
      Logger.timedCreate("Creating location spatial index...", "Spatial index created.") { () =>
        SpatialIndex(mergedResult.graph.getLocations) { l =>
          (l.lat,l.long)
        }
    }

    Logger.timed("Creating edges between stations.", "Transfer edges created.") { () =>
      val stationVertices = 
        Logger.timedCreate(" Finding all station vertices..."," Done.") { () =>
          mergedResult.graph.getVertices.filter(_.vertexType == StationVertex)
        }

      Logger.timed(" Iterating through stations to connect to street vertices..."," Done.") { () =>
        for(v <- stationVertices) {
          val extent =
            Projection.getBoundingBox(v.location.lat, v.location.long, 100)

          for(location <- index.pointsInExtent(extent)) {
            val t = mergedResult.graph.getVertexAtLocation(location)
            val duration = Walking.walkDuration(v.location,t.location)
            mergedResult.graph.addEdge(v,t,Time.ANY,duration)
          }
        }
      }
    }

    val graph = mergedResult.graph

    Logger.log(s"Graph Info:")
    Logger.log(s"  Edge Count: ${graph.edgeCount}")
    Logger.log(s"  Vertex Count: ${graph.vertexCount}")

    val packed =
      Logger.timedCreate("Packing graph...",
        "Packed graph created.") { () =>
        graph.pack
      }

    val packedIndex = GraphContext.createSpatialIndex(packed)

    GraphContext(packed,packedIndex,mergedResult.namedLocations,mergedResult.namedWays)
  }
}
