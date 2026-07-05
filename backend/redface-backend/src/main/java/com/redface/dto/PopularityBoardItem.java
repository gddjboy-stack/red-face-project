package com.redface.dto;

/**
 * API-2 人气看板条目。
 */
public class PopularityBoardItem {
    private Integer number;
    private String name;
    private String teamName;
    private Boolean isSpy;
    private long value;

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Boolean getIsSpy() { return isSpy; }
    public void setIsSpy(Boolean isSpy) { this.isSpy = isSpy; }
    public long getValue() { return value; }
    public void setValue(long value) { this.value = value; }
    public void setPopularityValue(long popularityValue) { this.value = popularityValue; }
}
