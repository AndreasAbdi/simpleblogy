<!DOCTYPE html>
<html>
<head>
  <#include "header.ftl">
</head>

<body>

  <#include "nav.ftl">

  <#list articles as article>
  ${article}
  </#list>

</body>
</html>
