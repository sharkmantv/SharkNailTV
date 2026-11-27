import type { TvView } from "@/scripts/tv/router"
import { navigate } from "astro:transitions/client"
import { registerFocusSection } from "@/scripts/tv/focus"

type Card = { title: string; href?: string; action?: "playlists"|"reload"|"exit" }
const cards: Card[] = [
  {title:"Live TV",href:"/tv/live"},
  {title:"Movies",href:"/tv/movies"},
  {title:"Series",href:"/tv/series"},
  {title:"Playlists",action:"playlists"},
  {title:"Settings",href:"/tv/settings"},
  {title:"Reload",action:"reload"},
  {title:"Account",href:"/tv/login"},
  {title:"Exit",action:"exit"},
]
const view: TvView = {
  mount(root) {
    root.className="sky-home-root"
    root.innerHTML='<div class="sky-home-grid" aria-label="SKY RETRO Home"></div>'
    const grid=root.querySelector<HTMLElement>(".sky-home-grid")!
    cards.forEach((card,i)=>{
      const item=document.createElement(card.href?"a":"button")
      item.className="sky-home-card"
      item.setAttribute("data-focus-key","sky:"+i)
      item.setAttribute("aria-label",card.title)
      if(card.href){
        item.setAttribute("href",card.href)
        item.addEventListener("click",e=>{e.preventDefault();void navigate(card.href!)})
      }else{
        (item as HTMLButtonElement).type="button"
        item.addEventListener("click",()=>{
          if(card.action==="playlists") document.getElementById("tv-nav-playlist-btn")?.click()
          else if(card.action==="reload") window.location.reload()
          else if(card.action==="exit"){
            const bridge=(window as any).AndroidDeviceInfo
            if(typeof bridge?.exitToLauncher==="function") bridge.exitToLauncher()
          }
        })
      }
      const art=document.createElement("span")
      art.className="sky-home-art"
      art.style.backgroundPosition=((i%4)*100/3)+"% "+(i<4?0:100)+"%"
      art.setAttribute("aria-hidden","true")
      item.appendChild(art);grid.appendChild(item)
    })
    const stop=registerFocusSection("sky-home",grid,{
      restrict:"self-only",defaultElement:".sky-home-card:first-child",
      leaveFor:{up:"",down:"",left:"",right:""}
    })
    const first=grid.querySelector<HTMLElement>(".sky-home-card")
    const raf=requestAnimationFrame(()=>first?.focus())
    return ()=>{cancelAnimationFrame(raf);stop();root.replaceChildren()}
  },
}
export default view
