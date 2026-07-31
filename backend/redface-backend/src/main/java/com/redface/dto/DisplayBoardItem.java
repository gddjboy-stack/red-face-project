package com.redface.dto;

/**
 * C20-5 大屏排行榜条目。
 *
 * <p>相比 {@link PopularityBoardItem} 增加服务端计算的 {@code rank}（名次），
 * 并<b>不再透出</b> {@code isSpy}——卧底身份属赛制机密，大屏为公开画面，一律不出。
 */
public class DisplayBoardItem {

    private int rank;
    private Integer number;
    private String name;
    private String teamName;
    private long value;

    public DisplayBoardItem() {
    }

    public DisplayBoardItem(int rank, Integer number, String name, String teamName, long value) {
        this.rank = rank;
        this.number = number;
        this.name = name;
        this.teamName = teamName;
        this.value = value;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }
}
