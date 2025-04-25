package com.iscas.crashtracker.client.crash;

/**
 * @Author hanada
 * @Date 2022/7/1 11:09
 * @Version 1.0
 */
public enum Strategy {
    NoSourceType, ExtendCGOnly,  NoKeyAPI, NoParaChain, NoAppDataTrace, NOParaChainANDDataTrace, FilterCallers,  FilterCandidates
}
