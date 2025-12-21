# Kotlin Partials

Kotlin Partials is a web framework for Kotlin (JVM) that allows you to build interactive web
applications with server-driven logic. It is inspired by [htmx](https://htmx.org/), but with a key difference:
the logic for how a request is handled resides on the server rather than being embedded in the HTML via attributes.

## Key Concepts

- **Server-Driven**: The server determines which parts of the page need to be updated in response to an event.
- **Kotlin Multiplatform**: Leverages Kotlin for both server-side (JVM) and client-side (JS) logic, ensuring type safety
  and code reuse.
- **Partial Updates**: Instead of reloading the entire page, Kotlin Partials updates only the specific elements that
  have changed.
- **SSE Support**: Built-in support for Server-Sent Events (SSE) allows the server to push updates to the client in
  real-time.

## How it Differs from HTMX

In HTMX, you often use attributes like `hx-target` and `hx-swap` to define how the UI should change when a request is
made.

With **Kotlin Partials**, you define an event handler (like `onClick`) on the server. When the event is triggered,
the server processes it and can choose to:

1. Redirect to a new page.
2. Refresh specific "partials" (parts of the page).
3. Push updates via an SSE connection.

This approach keeps your HTML clean and centralizes your application logic on the server.

## Getting Started

### 1. Define your Session and Page data

The page data is any state you want to maintain for a single page. It is serialized to the client and sent with every
request.

```kotlin
data class MySession(
  var username: String = "Guest"
) : PartialsSession(), Serializable

@Serializable
data class MyPageData(
  val counter: Int = 0
) : Serializable
```

### 2. Create a Page

Extend `PartialsPage` to define your page logic and content. The `process` method is called whenever a request is made.
The `refresh` method is used to update specific parts on the page by element id. The page is rendered again completely,
but only the parts defined with `refresh` will be sent to the browser.

```kotlin
class MyPage(
  request: Request,
  session: MySession,
  data: MyPageData
) : PartialsPage<MySession, MyPageData>(
  request,
  session,
  data
) {

  override fun process(): String? {
    if (request.get("action") == "increment") {
      // Logic to update data or session
      refresh("counter-section")
    }
    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    div {
      h1 { +"Welcome, ${session.username}" }

      div {
        id = "counter-section"
        +"Counter: ${data.counter}"
      }

      button {
        type = ButtonType.button
        onClick("action" to "increment")
        +"Increment"
      }
    }
  }
}
```

### 3. Initialize the Server

The first page in the mappings list will also be the default page.

```kotlin
fun main() {
  val server = createPartialsServer(
    port = 8080,
    session = { MySession() },
    "/index" to MyPage::class,
    "/other-page" to MyOtherPage::class,
  )
  server.start()
}
```

## Features

- **Event Handlers**: Easily attach `onClick`, `onChange`, `onSubmit`, etc., to your HTML elements.
- **Type-Safe HTML**: Uses `kotlinx.html` for building your UI.
- **Automatic State Management**: Page data is automatically serialized and sent back and forth, maintaining state
  across partial updates.
- **Real-time Updates**: Use `PartialsConnections` to push updates to clients from anywhere in your backend logic.

## Example projects

### Test page

In the project there is a click counter test example in the `nl.astraeus.partials.test` package.

### Todo example

Here is the [TodoMVC](https://todomvc.com/) example rewritten using Kotlin Partials:

- [Kotlin Partials Todo](https://github.com/rnentjes/kotlin-partials-todo)

### Chat example

There is also a simple chat example to show server side event support:

- [Kotlin Partials Chat](https://github.com/rnentjes/kotlin-partials-chat)

### Notes example

There is also a note taking tool example with a more complex UI:

- [Simple notes](https://github.com/rnentjes/simple-notes)

## Tips & tricks

### Form

There is no need to use `form` tags when using Kotlin Partials. There is already a `form` tag in the `PartialsPage`
class.
It's not possible to have multiple forms on a page.

### Buttons

Buttons with the type `submit` will cause a full page submit and refresh. Even though this works fine, the parameters
passed to the onClick
will not be sent to the server. Use type 'button' with an onClick instead.

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

Make sure the text content is added last:

```kotlin
button {
  onClick("action" to "increment")
  +"Increment"
}
```

## License

This project is licensed under the MIT License.
