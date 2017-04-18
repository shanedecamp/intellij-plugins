<#-- @ftlvariable name="action" type="java.lang.String" -->
<#-- @ftlvariable name="disabled" type="java.lang.String" -->
<#-- @ftlvariable name="stepNode" type="org.stepik.core.courseFormat.stepHelpers.FreeAnswerQuizHelper" -->
<#-- @ftlvariable name="text" type="java.lang.String" -->

<style>
    #text {
        display: block;
        margin: 10px auto;
        width: 100%;
        min-height: 200px;
    }
</style>

<#include "base.ftl">

<@quiz_content>
    <#if action != "get_first_attempt" && action != "need_login" >
    <textarea id="text" class="row" name="value"
              placeholder="Input your answer here" ${disabled!""}>${stepNode.getText()}</textarea>

        <#if stepNode.withReview()>
        ${stepNode.getStageText()}
            <#if stepNode.hasAction()>
            <a href="${stepNode.getLink()}">${stepNode.getActionName()}</a>
            ${stepNode.getActionHint()}
            </#if>
        <br>
        </#if>
    </#if>
</@quiz_content>