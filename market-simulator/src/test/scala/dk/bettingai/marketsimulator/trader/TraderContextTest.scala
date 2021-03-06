package dk.bettingai.marketsimulator.trader

import org.junit._
import Assert._
import dk.betex._
import api._
import Market._
import IBet.BetTypeEnum._
import IBet.BetStatusEnum._
import java.util.Date
import dk.bettingai.marketsimulator._

class TraderContextTest {

  private val betex = new Betex()
  private val market: IMarket = betex.createMarket(1l, "marketName", "eventName", 1, new Date(System.currentTimeMillis), new Runner(11, "runnerName1") :: new Runner(12, "runnerName2") :: Nil)

  private var lastBetId = 0l
  private val nextBetId = () => { lastBetId += 1; lastBetId }
  val ctx = new TraderContext(nextBetId(), 200, market, 0, 1000, Simulator(0.05, 1000), null)
  ctx.setEventTimestamp(1234)

  /**
   * Test scenarios for fillBet
   *
   */
  @Test
  def testFillBet {
    val bet1 = ctx.fillBet(2, 3, BACK, 11).get

    assertEquals(2, bet1.betSize, 0)
    assertEquals(3, bet1.betPrice, 0)
    assertEquals(BACK, bet1.betType)
    assertEquals(11, bet1.runnerId)

    val bet2 = ctx.fillBet(6, 3, BACK, 11).get

    assertEquals(4, bet2.betSize, 0)
    assertEquals(3, bet2.betPrice, 0)
    assertEquals(BACK, bet2.betType)
    assertEquals(11, bet2.runnerId)

    val bet3 = ctx.fillBet(6, 3, BACK, 11)

    assertEquals(None, bet3)

    val bet4 = ctx.fillBet(6, 3.1, BACK, 11).get

    assertEquals(6, bet4.betSize, 0)
    assertEquals(3.1, bet4.betPrice, 0)
    assertEquals(BACK, bet4.betType)
    assertEquals(11, bet4.runnerId)

    val bet5 = ctx.fillBet(6, 3.1, BACK, 12).get

    assertEquals(6, bet5.betSize, 0)
    assertEquals(3.1, bet5.betPrice, 0)
    assertEquals(BACK, bet5.betType)
    assertEquals(12, bet5.runnerId)

    val bet6 = ctx.fillBet(6, 3, LAY, 11).get

    assertEquals(6, bet6.betSize, 0)
    assertEquals(3, bet6.betPrice, 0)
    assertEquals(LAY, bet6.betType)
    assertEquals(11, bet6.runnerId)

  }

  /**
   * Test scenarios for placeHedgeBet
   *
   */

  @Test
  def testPlaceHedgeBetNoHedgeBetNoPricesToBet {

    market.placeBet(nextBetId(), 201l, 2, 3, LAY, 11l,1000)
    ctx.placeBet(2, 3, BACK, 11l)

    val hedgeBet: Option[IBet] = ctx.placeHedgeBet(11l)
    assertEquals(None, hedgeBet)
  }

  @Test
  def testPlaceHedgeBetLayHedgeBetIsPlaced {
    market.placeBet(nextBetId(), 201l, 2, 3, LAY, 11l,1000)
    market.placeBet(nextBetId(), 201l, 2, 4, BACK, 11l,1001)
    ctx.placeBet(2, 3, BACK, 11l)

    val hedgeBet: Option[IBet] = ctx.placeHedgeBet(11l)
    assertEquals(4, hedgeBet.get.betPrice, 0)
    assertEquals(1.5, hedgeBet.get.betSize, 0)
    assertEquals(LAY, hedgeBet.get.betType)
    assertEquals(11, hedgeBet.get.runnerId)

    /**No hedge bet is placed this time.*/
    val nextHedgeBet: Option[IBet] = ctx.placeHedgeBet(11l)
    assertEquals(None, nextHedgeBet)
    assertEquals(-0.5, ctx.risk(1000).ifWin(11), 0)
    assertEquals(-0.5, ctx.risk(1000).ifLose(11), 0)
  }

  @Test
  def testPlaceHedgeBetBackHedgeBetIsPlaced {
    market.placeBet(nextBetId(), 201l, 2, 3, LAY, 11l,1000)
    market.placeBet(nextBetId(), 201l, 2, 4, BACK, 11l,1001)
    ctx.placeBet(1.5, 4, LAY, 11l)

    val hedgeBet: Option[IBet] = ctx.placeHedgeBet(11l)
    assertEquals(3, hedgeBet.get.betPrice, 0)
    assertEquals(2, hedgeBet.get.betSize, 0)
    assertEquals(BACK, hedgeBet.get.betType)
    assertEquals(11, hedgeBet.get.runnerId)

    /**No hedge bet is placed this time.*/
    val nextHedgeBet: Option[IBet] = ctx.placeHedgeBet(11l)
    assertEquals(None, nextHedgeBet)
    assertEquals(-0.5, ctx.risk(1000).ifWin(11), 0)
    assertEquals(-0.5, ctx.risk(1000).ifLose(11), 0)
  }

  /**Test for getBet.*/
  @Test
  def getBet_unmatchedBetIsReturned {
    val bet = ctx.placeBet(2, 2.1, BACK, 11)
    assertEquals(Bet(1, 200, 2, 2.1, BACK, U, 1, 11, 1234,None) :: Nil, ctx.getBet(bet.betId))
  }

  @Test
  def getBet_fullyMatchedBetIsReturned {
    val bet = ctx.placeBet(2, 2.1, BACK, 11)
    market.placeBet(101, 1001, 2, 2.1, LAY, 11,1000)
    assertEquals(Bet(1, 200, 2, 2.1, BACK, M, 1, 11, 1234,Some(1000)) :: Nil, ctx.getBet(bet.betId))
  }

  @Test
  def getBet_partiallyMatchedBetIsReturned {
    val bet = ctx.placeBet(10, 2.1, BACK, 11)
    market.placeBet(101, 1001, 7, 2.1, LAY, 11,1000)
    assertEquals(Bet(1, 200, 3, 2.1, BACK, U, 1, 11, 1234,None) :: Bet(1, 200, 7, 2.1, BACK, M, 1, 11, 1234,Some(1000)) :: Nil, ctx.getBet(bet.betId))
  }

  @Test
  def getBet_betNotFound {
    val bet = ctx.placeBet(10, 2.1, BACK, 11)
    market.placeBet(101, 1001, 7, 2.1, LAY, 11,1000)
    assertEquals(Nil, ctx.getBet(bet.betId+1))
  }

  @Test
  def getBet_betIsCancelled {
    val bet = ctx.placeBet(10, 2.1, BACK, 11)
    market.cancelBet(bet.betId)
    assertEquals(Nil, ctx.getBet(bet.betId))
  }

  /**Tests scenarios for registerTrader*/

  @Test
  def testRegisterTrader {
    val traderContext = ctx.registerTrader
    assertEquals(1234, traderContext.getEventTimestamp)
  }

}