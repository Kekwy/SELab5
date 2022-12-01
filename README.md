# 软工 Lab5

> 可弟WZ Kekwy
>
> https://blog.kekwy.com
>
> 11月29日

## 1. 项目简介

### 1.1 介绍

本项目为一个等价性确认工具，输入为等价性判断工具的两个 csv 文件和程序源码，为用户依次展示每个程序对的 diff 界面，获取用户反馈，根据用户反馈的确认结果生成新的等价集，并写入 csv 文件。

其他特性如下：

- 可检查是否出现用户确认结果不自洽的情况；
- 通过使用并查集减少需要确认的程序对：
- 将结果写入文件时可以选择更新原文件或写入新建文件；
- 可通过修改配置文件，自定义与用户交互的端口号；
- 自动唤起默认浏览器访问服务页面。

运行环境：Java16

### 1.2 开发

> 项目远程仓库：https://github.com/Kekwy/SELab5

本项目通过 maven 进行管理，主要依赖如下：

```xml
<!-->根据文本差异生成统一差异格式的工具<-->
<dependency>
	<groupId>io.github.java-diff-utils</groupId>
	<artifactId>java-diff-utils</artifactId>
	<version>4.12</version>
</dependency>
<!-->CSV文件的读取工具<-->
<dependency>
	<groupId>net.sourceforge.javacsv</groupId>
	<artifactId>javacsv</artifactId>
	<version>2.0</version>
</dependency>
<!-->maven打包工具，可连同依赖项一起打包<-->
<dependency>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-assembly-plugin</artifactId>
	<version>3.4.2</version>
</dependency>
```

在 HTML 代码中引用的 CSS 和 JS 代码如下：

```tex
https://github.com/1506085843/fillDiff/tree/main/src/main/resources/css/github.min.	
https://github.com/1506085843/fillDiff/tree/main/src/main/resources/css/diff2html.min.css
https://github.com/1506085843/fillDiff/tree/main/src/main/resources/js/diff2html-ui.min.js
```

### 1.3 展示

用户使用界面效果图：

<img src="https://assets.kekwy.com/images/image-20221201092421934.png" alt="image-20221201092421934" style="zoom:150%;" />

<img src="https://assets.kekwy.com/images/image-20221201092434440.png" alt="image-20221201092434440" style="zoom:150%;" />



## 2. 具体实现

### 2.1 运行逻辑

根据用户指定的 csv 文件路径和源码文件根目录，读取程序对以及对应的源码，再根据所选定程序对的源码生成统一差异格式（unifiedDiff）字符串并进行加工处理。

将上述差异字符串插入到一段 HTML 代码中，每当用户通过浏览器访问 `http://localhost:{自定义端口}` 时，返回该段 HTML 代码。该段代码将被浏览器解析渲染为一个用户可交互的网页。工作线程则阻塞住，直到用户通过点击网页上对应的 button，使用 POST 方法提交反馈结果。

工作线程获取到用户确认结果后，据此将当前程序对归类（等价 / 不等价）。之后选取下一个程序对，重复上述操作，直到待确认队列为空。

### 2.2 CheckToolController

> 等价确认工具的主要工作流程。

```java
public void run() {
	...
	while (reader.readRecord()) {
        // 获取当前的程序对
		String[] pair = reader.getValues();
        // 将两个程序的源码分行读出
		List<String> original = Files.readAllLines(...);
		List<String> revised = Files.readAllLines(...);
		boolean isEqual = true;
        //构造程序对的 csv 格式
		String pairRecord = pair[0] + "," + pair[1];
		// 生成 unifiedDiff
        String diffString = DiffTextGenerator.generate(pair[0], pair[1], original, revised);
		// 若 unifiedDiff 为空，则两文件内容完全相同，不必确认
        if (diffString != null) {
            // 设置前端显示的内容，并等待用户确认结果
            // 若返回 1，则说明用户确认当前程序对等价
			isEqual = server.send(diffString) == 1;
		}
		if (isEqual) {
            // 若等价，则加入等价程序对列表
			equalPairs.add(pairRecord);
		} else {
            // 若不等价，则加入不等价程序对列表
			inequalPairs.add(pairRecord);
		}
        // 两列表内容将被分别写入 equal.csv 与 inequal.csv
	}
    ...
}
```

### 2.3 LocalWebServer

> 向用户发送前端页面，接收用户确认结果，并通知工作线程。

1. 启动本地服务器：

   ```java
   createHttpServer(new InetSocketAddress(port), 100); // 在所设置的端口上启动服务
   httpserver.createContext("/", this::handle);		// 设置根目录的处理方法
   ```

   这样通过浏览器访问本地主机的相应端口时，会调用 `handle` 方法处理 HTTP Request。

2. 处理到来的 HTTP Request：

   ```java
   public void handle(HttpExchange exchange) throws IOException {
   	boolean flag = true;
   	if (Objects.equals(exchange.getRequestMethod(), "POST")) {
           // 若为 POST 方法，则可能为用户的确认结果，
           // 调用 handlePost 进行处理
   		flag = handlePost(exchange);
   	}
   	if (!flag) {
           // 若请求体中不包含有效数据，
           // 则返回 400: Bad Request
   		exchange.sendResponseHeaders(400, 0);
   		exchange.getResponseBody().write("Bad Request.".getBytes());
   		exchange.getResponseBody().close();
   		return;
   	}
       ...
       // 构造 HTML 代码
   	response = TEMPLATE.formatted(GITHUB_CSS, DIFF2HTML_CSS, DIFF2HTML_JS, responseMessage);
   	...
       // 写入 HTTP Response
   	exchange.sendResponseHeaders(200, 0);
   	OutputStream os = exchange.getResponseBody();
   	os.write(response.getBytes(StandardCharsets.UTF_8));
   	os.close();
   }
   ```

   处理 POST 方法：

   ```java
   public boolean handlePost(HttpExchange exchange) throws IOException {
   	...
       // 通过正则表达式匹配请求体表单中的值，并设置 feedback。
   	if (Objects.equals(matcher.group(2), "equal")) {
   		helper(1);
   	} else if (Objects.equals(matcher.group(2), "inequal")) {
   		helper(0);
   	} else { // 没有发现有效表单数据，则返回 false
   		return false;
   	}
   	return true;
   }
   ```

3. 线程同步：

    * responseMessage、responseReady 在 mutexResponse 上同步；
    * feedback、feedbackReady 在 mutexFeedback 上同步。

   ```java
   /**
    * 被工作线程调用，向服务器发送消息并等待用户反馈
    */
   public int send(String response) {
   	synchronized (mutexResponse) {
           // 设置 responseMessage
   		this.responseMessage = response;
           // 将 responseMessage 状态置为可用
   		responseReady = true;
           // 若存在阻塞的 handler，则唤醒
   		mutexResponse.notifyAll();
   	}
   	synchronized (mutexFeedback) {
           // 若 feedback 状态不可用，
           // 说明当前程序对尚未获得用户确认结果，
   		if (!feedbackReady) {
               // 阻塞，直到收到用户确认结果
   			mutexFeedback.wait();
   		}
           // 该确认结果已被工作线程读取，
           // 故将 feedback 状态置为不可用。
   		feedbackReady = false;
   		return feedback;
   	}
   }
   
   /**
    * 当服务器接收到新的 HTTP Request 时被调用
    */
   public void handle(HttpExchange exchange) throws IOException {
   	...	
       synchronized (mutexResponse) {
           // 若 responseMessage 不可用，
           // 则阻塞，直到工作线程交付新的 responseMessage
   		if (!responseReady) {
   			mutexResponse.wait();
   		}
           // 使用 responseMessage 构造 HTTP Response，
           // 并返回给用户。
   	}
       ...
   }
   
   /**
   * 根据用户的反馈信息设置 feedback
   */
   private void helper(int feedback) {
   	boolean flag;
   	synchronized (mutexResponse) {
   		flag = responseReady;
           // 当前 responseMessage 代表的程序对获得确认结果，
           // 故将 responseMessage 的状态置为不可用。
   		responseReady = false;
   	}
       // 判断当前确认结果是否有与之对应的待确认程序对
   	if (flag) {
   		synchronized (mutexFeedback) {
               // 设置用户对当前程序对的确认结果
   			this.feedback = feedback;
               // 将 feedback 状态置为可用
   			feedbackReady = true;
               // 若工作线程阻塞，则唤醒
   			mutexFeedback.notify();
   		}
   	}
   }
   ```

4. HTML 代码框架：

   以文本块的形式储存在 LocalWebServer 类的静态常量 TEMPLATE 中：

   - 使用 `Diff2HtmlUI` 绘制 diff 界面：

     ```html
     <script>
     	// 使用工作线程提交的 diffString 替换此处的值	
     	const diffString = `%s`;
     							
     	document.addEventListener('DOMContentLoaded', function () {
     		var targetElement = document.getElementById('myDiffElement');
     		var configuration = {
     				...
     		};
     		var diff2htmlUi = new Diff2HtmlUI(targetElement, diffString, configuration);
     		diff2htmlUi.draw();
     		diff2htmlUi.highlightCode();
     	});
     </script>
     ```

   - 网页的布局：

     ```html
     <body>
         <!-->用户可交互的按钮<-->
     	<div class="buttons">
     		请选择：
     		<input value="确认等价" type="button" id="equalButton">
     		<input value="确认不等价" type="button" id="inequalButton">
     	</div>
         <!-->diff界面将被绘制在该控件上<-->
     	<div id="myDiffElement"></div>
     </body>
     ```

   - 与按钮绑定的 js 代码，实现点击按钮向后端发送 POST 请求：

     ```html
     <script>
     	// 绑定事件处理方法
         document.getElementById("equalButton").onclick = function() {
     		helper("equal");
     	}
     	document.getElementById("inequalButton").onclick = function() {
     		helper("inequal");
     	}
     	function helper(v) {
     		var temp = document.createElement("form");	  // 创建虚拟表单
     		temp.action = "/";							  // 设置请求的url
     		temp.method = "post";						  // 设置请求方法为 POST
     		temp.style.display = "none";				  // 隐藏表单
     		var opt = document.createElement("textarea"); // 创建一个文本框元素
     		opt.name = "result";				// 设置元素名称
     		opt.value = v;						// 设置元素值
     		temp.appendChild(opt);				// 将该元素加入表单
     		document.body.appendChild(temp);
     		temp.submit(); // 提交表单
             return temp;
     	}
     </script>
     ```

### 2.4 com.kekwy.util

> 自定义工具包

该包下有三个工具类：

- DatePrefix：根据系统时间生成前缀字符串，用于创建新文件保存确认结果时的文件命名；
- DiffTextGenerator：使用 java-diff-utils 对比源码文件生成 unifiedDiff。由于 unifiedDiff 中只包含有差异的行，故需要将两个文件中相同行的内容插入到对应的位置，生成最终可被前端 js 代码解析的 diff 文本；
- DisjointSetUnion：简单的并查集，主要用于减少人工确认次数。



## 3. 使用演示

1. 首先确保配置文件 config.properties 与 jar 包在同一目录下，再使用如下指令启动工具：

   ```shell
   java -jar lab5-1.0.jar -dir {0} -csv {1}
   ```

   - {0} 位置为源码文件的根目录；

     解释：由于上一次实验中要求的输出格式为 `input/子目录/文件名`，故需要提供的根目录为 input 文件夹所在目录的路径；

   - {1} 位置为判断工具输出的 csv 文件的目录。

   如在下图目录中输入以下命令：

   ```shell
   java -jar lab5-1.0.jar -dir . -csv ./output
   ```

   <img src="https://assets.kekwy.com/images/image-20221201170938756.png" alt="image-20221201170938756" style="zoom:150%;" />

2. 输入命令后会自动弹出浏览器窗口，首先开始人工确认判断工具输出的等价结果：

   <img src="https://assets.kekwy.com/images/image-20221201171131921.png" alt="image-20221201171131921" style="zoom:150%;" />

   * 特别说明：此处由于 clog 的输出不能通过重定向获取，故判断工具认为其输出为空，将上述两程序判断为了等价程序。

   用户通过点击页面上的两个按钮，确认当前程序是否等价。

   直到返回空白页，结束确认：

   <img src="https://assets.kekwy.com/images/image-20221201171542733.png" alt="image-20221201171542733" style="zoom:150%;" />

3. 返回查看 shell，提示是否需要确认判断工具生成的不等价结果：

   <img src="https://assets.kekwy.com/images/image-20221201171705802.png" alt="image-20221201171705802" style="zoom:150%;" />

   * 特别说明：在判断工具正常运行的情况下，将两程序视为不等价，则一定是找到了两程序对某一组输入产生了不同的输出的情况。故一般情况下不等价的判断结果都是准确的，不需要进行人工确认。当然也可能会产生本次实验中 clog 的输出不能重定向，干扰了判断结果的情况。此处用户可以视情况自行选择是否要继续判断。

4. 在完成上述步骤后，程序提示我们是否需要更新原有的 csv 文件：

   <img src="https://assets.kekwy.com/images/image-20221201172149276.png" alt="image-20221201172149276" style="zoom:150%;" />

   由于文件重写操作不可逆，这里不建议选择更新。

5. 我们选择 no，新建文件保存数据：

   <img src="https://assets.kekwy.com/images/image-20221201172313254.png" alt="image-20221201172313254" style="zoom:150%;" />

   会给出我们结果文件的路径，查看相应文件，即可获取人工确认的结果。

* 需要注意的是，由于按钮是通过 POST 方法发送表单数据的方式进行确认结果的提交，对于某些浏览器（如 Firefox），当你点击浏览器的刷新键时，会重复发送表单数据，而程序只会认为用户做出了与上次相同的判断，继续正常运行，返回下一组要确认的程序对。这可能会产生预期之外的结果。故已经进行过确认操作之后，尽可能**不要手动刷新页面**。



## 4. 额外功能

1. 可检查是否出现用户确认结果不自洽的情况：

   ```java
   public void run() {
       // 创建空并查集
   	DisjointSetUnion<String> dsUnion = new DisjointSetUnion<>();
       // key - 程序
       // valuse - 所有与key程序不等价的其他程序
   	Map<String, List<String>> inequalMap = new HashMap<>();
   	...	
       while (true) {
   		...
   		while (reader.readRecord()) {
   			...
   			if (isEqual) { // 若人工确认两程序等价
                   // 定义一个检测函数，主要目的为避免代码重复
   				BiConsumer<String, String> helper = (String p1, String p2) -> {
                       // 遍历所有与p1不等价的程序，
                       // 若其中有程序与p2存在直接或间接的等价关系，
                       // 则违反了等价性的传递性，抛出异常
   					for (String s : inequalMap.get(p1)) {
   						String root1 = dsUnion.find(s);
   						String root2 = dsUnion.find(p2);
   						if (Objects.equals(root1, root2)) {
                               // 抛出异常前将当前结果写入文件
   							toCSVFile(equalPairs, inequalPairs, false);
   							throw new RuntimeException("人工确认结果中存在矛盾项:\n"
   								+ p2 + "与" + p1 + "等价;\n"
   								+ p2 + "与" + s + "等价;\n"
   								+ "而" + p1 + "与" + s + "不等价");
   						}
   					}
   				};
                   // 两次调用上述函数
   				helper.accept(pair[0], pair[1]);
   				helper.accept(pair[1], pair[0]);
                   // 在并查集中关联两程序
   				dsUnion.union(pair[0], pair[1]);
                   ...
   			} else { // 经人工确认后不等价
                   // 互相加入对方的不等价集中
   				inequalMap.get(pair[0]).add(pair[1]);
   				inequalMap.get(pair[1]).add(pair[0]);
   				...
   			}
   		}
       }
       ...
   }
   ```

2. 通过使用并查集减少需要确认的程序对：

   ```java
   // 由于等价性的传递性，一组经人工确认后建立了间接等价关系的两个程序，可以跳过人工确认
   if (!Objects.equals(dsUnion.find(pair[0]), dsUnion.find(pair[1]))) {
   	String diffString = DiffTextGenerator.generate(pair[0], pair[1], original, revised);
   	if (diffString != null) {
   		isEqual = server.send(diffString) == 1;
   	}
   }
   ```

3. 可通过修改配置文件，自定义与用户交互的端口：

   ```java
   props.load(new FileInputStream("./config.properties"));
   SERVER_PORT = Integer.parseInt((String) props.get("port"));
   ```

   配置文件 config.properties：

   <img src="https://assets.kekwy.com/images/image-20221201164421063.png" alt="image-20221201164421063" style="zoom:150%;" />

4. 自动唤起默认浏览器访问服务页面：

   ```java
   Desktop.getDesktop().browse(new URI("http://localhost:" + SERVER_PORT));
   ```



## 5. git 操作

1. 暂存：

   <img src="https://assets.kekwy.com/images/image-20221117181249852.png" alt="image-20221117181249852" style="zoom:150%;" />

2. 提交：

   <img src="https://assets.kekwy.com/images/image-20221117181414730.png" alt="image-20221117181414730" style="zoom:150%;" />

   `git status`：

   <img src="https://assets.kekwy.com/images/image-20221117181814335.png" alt="image-20221117181814335" style="zoom:150%;" />

3. 回退：

   `--soft`：

   <img src="https://assets.kekwy.com/images/image-20221117182346999.png" alt="image-20221117182346999" style="zoom:150%;" />

   `--hard`：

   <img src="https://assets.kekwy.com/images/image-20221117182806955.png" alt="image-20221117182806955" style="zoom:150%;" />

4. 新建分支并签出到新分支：

   <img src="https://assets.kekwy.com/images/image-20221117185708666.png" alt="image-20221117185708666" style="zoom:150%;" />

5. 在新分支创建新的提交：

   <img src="https://assets.kekwy.com/images/image-20221117185945757.png" alt="image-20221117185945757" style="zoom:150%;" />

6. 返回主分支进行合并：

   <img src="https://assets.kekwy.com/images/image-20221117190135931.png" alt="image-20221117190135931" style="zoom:150%;" />

7. 解决冲突：

   新建分支后，两分支分别创建一个提交。再回到主分支进行合并：

   <img src="https://assets.kekwy.com/images/image-20221117190801545.png" alt="image-20221117190801545" style="zoom:150%;" />

   尝试合并发现存在冲突：

   <img src="https://assets.kekwy.com/images/image-20221117190929602.png" alt="image-20221117190929602" style="zoom:150%;" />

   使用命令 `git mergetool` 打开 git 的分支合并工具：

   ![image-20221117191705059](https://assets.kekwy.com/images/image-20221117191705059.png)

   在下方编辑框中进行编辑后保存：

   <img src="https://assets.kekwy.com/images/image-20221117192441620.png" alt="image-20221117192441620" style="zoom:150%;" />

   再创建新的提交，冲突解除：

   <img src="https://assets.kekwy.com/images/image-20221117192608124.png" alt="image-20221117192608124" style="zoom:150%;" />

8. git 日志图：

   <img src="https://assets.kekwy.com/images/image-20221201172912321.png" alt="image-20221201172912321" style="zoom:150%;" />

   由于本次实验中进行分支合并时，忘记添加参数 `--no-ff`（即使可以快进，也创建合并提交），导致所有的合并操作都对被合并分支进行了快进，所有 git 日志图中只有一条线。



【感谢评阅】
