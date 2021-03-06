package commonspace.graph

import commonspace._

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import scala.collection.mutable

class PackedGraphSpec extends FunSpec
                         with ShouldMatchers {
  describe("PackedGraph") {
    it("should pack a graph correctly.") {
      val unpacked = SampleGraph.noTimes
      val packed = unpacked.pack()
      
      val packedToUnpacked = (for(v <- 0 until packed.vertexCount) yield {
        val location = packed.locations.getLocation(v)
        unpacked.getVertices.find(_.location == location) match {
          case Some(vertex) =>
            (v,vertex)
          case _ =>
            sys.error(s"Could not find vertex $v in unpacked graph.")
        }
      }).toMap

      for(v <- packedToUnpacked.keys) {
        val unpackedEdges = packedToUnpacked(v).edges
        val packedEdges = mutable.ListBuffer[Edge]()
        packed.foreachOutgoingEdge(v,0) { (t,w) =>
          packedEdges += Edge(packedToUnpacked(t),Time.ANY,Duration(w))
        }
        packedEdges.sortBy(_.target.location.lat).toSeq should be 
           (unpackedEdges.toSeq.sortBy(_.target.location.lat).toSeq)
      }
    }

    it("should return proper outgoing edges for times.") {
      val packed = SampleGraph.withTimes.pack()

      // No edges past time 100
      for(v <- packed) { 
        var c = 0
        packed.foreachOutgoingEdge(v, 101) { (t,w) => c += 1 }
        c should be (0)
      }

      val v5 = packed.locations.getVertexAt(5.0,1.0)
      packed.foreachOutgoingEdge(v5,20) { (t,w) =>
        w should be ((50-20) + 5)
      }

      val v7 = packed.locations.getVertexAt(7.0,1.0)
      packed.foreachOutgoingEdge(v7,20) { (t,w) =>
        w should be ((70-20) + 7)
      }
    }

    it("should return proper outgoing edges for times and any times.") {
      val packed = SampleGraph.withTimesAndAnyTimes.pack()

      for(i <- 1 to 10) {
        val v = packed.locations.getVertexAt(i.toDouble,1.0)
        packed.foreachOutgoingEdge(v,50) { (t,w) =>
          val waitTime =  i*10 - 50
          if(waitTime < 0) {
            //Should be the AnyTime
            w should be (20)
          } else {
            if(waitTime + i < 20) {
              w should be (waitTime + i)
            } else { 
              w should be (20) 
            }
          }
        }
      }
    }
  }                           
}

