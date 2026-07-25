package org.wikipedia.feed.monthheader

import org.wikipedia.feed.model.Card
import org.wikipedia.feed.model.CardType
import org.wikipedia.util.DateUtil

class MonthHeaderCard(private val age: Int) : Card() {

    override fun title(): String {
        return DateUtil.getFeedCardMonthString(age)
    }

    override fun type(): CardType {
        return CardType.MONTH_HEADER
    }
}
