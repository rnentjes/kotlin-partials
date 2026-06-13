# Kotlin Partials

Kotlin Partials is a web framework for Kotlin (JVM) that allows you to build interactive web
applications with server-driven logic. It is inspired by [htmx](https://htmx.org/), but with a key difference:
the logic for how a request is handled resides on the server rather than being embedded in the HTML via attributes.

## How it Differs from HTMX

In HTMX, you often use attributes like `hx-target` and `hx-swap` to define how the page should be updated when
a request is made.

With **Kotlin Partials**, you define all the HTML with [kotlinx.html](https://github.com/Kotlin/kotlinx.html) on the
server.
For the events you define event handlers (like `onClick`) in the html builders on the server.
When the event is triggered, the server processes it, and you can then choose to:

1. Redirect to a new page.
2. Refresh specific "partials" (parts of the page).
3. Push updates via an SSE connection.

This way the logic to process events and how it affects the html view is close together.

## Getting Started

### Available on Maven Central

Add the following dependency to the Gradle build file:

    implementation("nl.astraeus:kotlin-partials:4.2.1")

### 1. Create a Page

Extend `PartialsPage` to define your page logic and content. The page class must have a no-arg constructor.
The `process` method is called whenever a request is made. In the process method you can call `update` with
a partial to specify which parts of the page should be updated. Only the partials passed to `update` will be sent to the browser.

```kotlin
class MyPage : PartialsPage<MySession, MyPageData>(
  { MyPageData() } // page data initializer
) {

  override fun process(): String? {
    request.value("action") { action ->
      if (action == "increment") {
        data.counter++

        update(counterSection)
      }
    }

    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    div {
      h1 { +"Welcome, ${session.username}" }

      partial(counterSection)

      button {
        // type button to prevent full page submit
        type = ButtonType.button
        onClick("action" to "increment")
        +"Increment"
      }
    }
  }

  val counterSection by partial { _, _ ->
    div {
      +"Counter: ${data.counter}"
    }
  }
}
```

#### Page lifecycle

You don't define `request`, `session` and `data` parameters in the constructor. These are set after the page object is
created. This means you cannot access these members in the constructor or init.

If you need to initialize something with data from these members then overwrite the `onInit` method for this.

### 2. Define your Session and Page data

The page data is any state you want to maintain for a single page. It is serialized to the client and sent with every
request. It should have a single constructor that can be called without any arguments. There is a NoData class if no
page data is required. The page data must implement `Serializable`.

The session is your http session and is available on any page for a single user. A new session is made with the factory
method passed to the createPartialsServer method.

```kotlin
data class MySession(
  var username: String = "Guest"
) : PartialsSession(), Serializable

data class MyPageData(
  var counter: Int = 0
) : Serializable
```

### 3. Initialize the Server

Create and start the server. The first page in the mappings list will also be the default page.

```kotlin
fun main() {
  val server = createPartialsServer<MySession>(
    port = 8080,
    session = { MySession() }, // session initializer
    "/index" to MyPage::class,
    "/other-page" to MyOtherPage::class,
  )
  server.start()
}
```

### 4. Reuse partials

Partials can be defined outside a page and reused by multiple pages. Pass the data needed by the partial when you render
or update it.

```kotlin
val messagePartial by partial { message, _ ->
  div {
    +"Message: ${message ?: "No message"}"
  }
}
```

```kotlin
class MyPage : PartialsPage<MySession, MyPageData>({ MyPageData() }) {

  override fun process(): String? {
    request.value("action") { action ->
      if (action == "change-message") {
        update(messagePartial, "Updated message")
      }
    }

    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    div {
      partial(messagePartial, "Initial message")

      button {
        type = ButtonType.button
        onClick("action" to "change-message")
        +"Change message"
      }
    }
  }
}
```

#### Multiple instances of the same partial

If the same partial is used more than once on a page, pass an id to keep the rendered elements separate:

```kotlin
partial(messagePartial, "First", id = 1)
partial(messagePartial, "Second", id = 2)
```

Use the same id when updating one of them:

```kotlin
update(messagePartial, "Changed", id = 2)
```

#### Redirects

Return a url from `process` to redirect. For a partials request the client will navigate to that url. For a normal
request the server returns a 302 redirect.

## Features

- **Event Handlers**: Easily attach `onClick`, `onChange`, `onSubmit`, etc., to your HTML elements.
- **Type-Safe HTML**: Uses [kotlinx.html](https://github.com/Kotlin/kotlinx.html) for building your UI.
- **Automatic State Management**: Page data is automatically serialized and sent back and forth, maintaining state
  across partial updates.
- **Real-time Updates**: Use `PartialsConnections` to push updates to clients from anywhere in your backend logic.
- **File Drop and Passkeys**: Helpers are available for file drops and passkey register/login events.

## Example projects

### Test page

In the project there are test pages in the `nl.astraeus.partials.test` package.

### Todo example

Here is the [TodoMVC](https://todomvc.com/) example rewritten using Kotlin Partials:

- [Kotlin Partials Todo](https://github.com/rnentjes/kotlin-partials-todo)

### Chat example

There is also a simple chat example to show server side events support:

- [Kotlin Partials Chat](https://github.com/rnentjes/kotlin-partials-chat)

### Notes example

And there is an application to take notes with a more complex UI:

- [Developer notes](https://github.com/rnentjes/developer-notes)

## Tips & tricks

### Form

There is no need to use `form` tags when using Kotlin Partials. There is already a `form` tag in the `PartialsPage`
class.
It's not possible to have multiple forms on a page.

### Buttons

Buttons with the type `submit` will cause a full page submit and refresh. Even though this works fine, the parameters
passed to the onClick will not be sent to the server. Use type 'button' with an onClick instead.

### Request

In the request object there are some helpers to handle the requests. For example the value method to get a value from a
request:

```kotlin

// in a PartialsPage class
override fun process(): String? {
  request.value("action") { action ->
    when (action) {
      "save" -> {
        // save the form
      }
      "cancel" -> {
        // don't save and return
      }
    }
  }

  return null
}
```

### kotlinx-html order of attributes

The text content of an element always has to be added last, or you will get the error:

    java.lang.IllegalStateException: You can't change tag attribute because it was already passed to the downstream
        at kotlinx.html.consumers.DelayedConsumer.onTagAttributeChange(delayed-consumer.kt:16)

So instead of:

```kotlin
button {
  +"Increment"
  onClick("action" to "increment")
}
```

Make sure the text content is always added last:

```kotlin
button {
  onClick("action" to "increment")
  +"Increment"
}
```

### Server configuration

The server uses undertow under the hood. If you need a different configuration, you can create your own chain of
`HttpHandler`s. See the [PartialsServer](src/jvmMain/kotlin/nl/astraeus/partials/PartialsServer.kt) file.

## License

This project is licensed under the MIT License.
