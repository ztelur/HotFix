package dodola.patch

import javassist.ClassClassPath
import javassist.ClassPool
import javassist.CtClass
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.util.jar.JarEntry
import java.util.jar.JarFile

public class HotFixPlugin implements Plugin<Project> {
    private static final String PATCH = "PATCH"
    private HashSet<String> includePackage
    private HashSet<String> excludeClass
    private ClassPool mClassPool;
    private String buildPath = "build/intermediates/classes/"+PATCH
    private String outputPath
    def debugOn

    private void initClassPool() {
        mClassPool = ClassPool.getDefault();
        // buildDir 是项目的build class目录,就是我们需要注入的class所在地
        mClassPool.appendClassPath(buildPath)
        //TODO:需要优化
//        mClassPool.appendClassPath(project(':hackdex').buildDir
//                .absolutePath + '/intermediates/classes/'+PATCH); //autoLoader的位置
    }
    private void readHotFixExtension() {
        def extension = project.extensions.findByName("hotfix") as HotFixExtension
        includePackage = extension.includePackage
        excludeClass = extension.excludeClass
        debugOn = extension.debugOn
    }
    private void initOutputDir() {
        outputPath = "${project.buildDir}/outputs/patch"
        File outputDir = new File(outputPath)
        outputDir.mkdirs()
    }
    @Override
    void apply(Project project) {
        println("apply the project")
        project.extensions.create("hotfix", HotFixExtension, project)
//        readHotFixExtension()
        project.afterEvaluate {
            initClassPool()
            project.android.applicationVariants.each { variant ->
                println("meet when ${variant.name}")
                String upperName = variant.name.capitalize()
                if (upperName.toUpperCase().contains(PATCH)) {
                    println("process when ${variant.name.capitalize()}")

                    outputPath = "${project.buildDir}/outputs/patch"
                    File outputDir = new File(outputPath)
                    outputDir.mkdirs()


                    def preDexTask = project.tasks.findByName("preDex${variant.name.capitalize()}")
                    def dexTask = project.tasks.findByName("dex${variant.name.capitalize()}")
                    def proguardTask = project.tasks.findByName("proguard${variant.name.capitalize()}")

                    if (preDexTask) {
                        def jarBeforePreDex = "jarBeforePreDex${variant.name.capitalize()}"
                        project.task(jarBeforePreDex) << {
                            Set<File> inputFiles = preDexTask.inputs.files.files
                            inputFiles.each { inputFile ->
                                //判断这个jar是否可以处理
                                if (shouldProcessPreDexJar(inputFile.getAbsolutePath())) {
                                    processJarWithJavassist(inputFile)
                                }
                            }
                        }
                        def jarBeforePreDexTask = project.tasks[jarBeforePreDex]
                        jarBeforePreDexTask.dependsOn preDexTask.taskDependencies.getDependencies(preDexTask)
                        preDexTask.dependsOn jarBeforePreDexTask

                        def hotfixClassBeforeDex = "hotfixClassBeforeDex${variant.name.capitalize()}"
                        project.task(hotfixClassBeforeDex) << {
                            Set<File> inputFiles = dexTask.inputs.files.files
                            inputFiles.each { inputFile ->
                                def path = inputFile.absolutePath
                                println("file path ${path}")
                                if (path.endsWith(".class") &&
                                        !path.contains("/R\$") &&
                                        !path.endsWith("/R.class") &&
                                        !path.endsWith("/BuildConfig.class")) {
                                    // if have not right annotation,don't process
                                    processWithJavassist(inputFile)
                                }
                            }
                        }
                        def classBeforeDexTask = project.tasks[hotfixClassBeforeDex]
                        classBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
                        dexTask.dependsOn classBeforeDexTask
                    }
                }
            }
        }
    }
    public  boolean shouldProcessPreDexJar(String path) {
        return path.endsWith("classes.jar") && !path.contains("com.android.support") && !path.contains("/android/m2repository");
    }
    private void processWithJavassist(final File file) {
        if (file == null) {
            return
        }
        println(file.getAbsolutePath()+"//")
        //下面的操作比较容易理解,在将需要关联的类的构造方法中插入引用代码
        CtClass c = mClassPool.makeClass(new FileInputStream(file))
        //先判断annotation的类型，决定是否添加到jar中去
        Object[] annotations = c.getAnnotations()
        boolean shouldProcess = shouldProcess(annotations)
        if (!shouldProcess) {
            return;
        }
        if (c.isFrozen()) {
            c.defrost()
        }
        println("====添加构造方法====")
        def constructor = c.getConstructors()[0];
        constructor.insertBefore("System.out.println(dodola.hackdex.AntilazyLoad.class);")
        c.writeFile(buildPath)
        c.writeFile(outputPath)
    }

    public processJarWithJavassist(File jarFile) {
        println("processJar ${jarFile.getAbsolutePath()}")
        if (jarFile) {
            def file = new JarFile(jarFile);
            Enumeration enumeration = file.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                println("process${jarEntry.name}")
                String entryName = jarEntry.getName();
                //判断这个类是否可以处理
                if (shouldProcessClassInJar(entryName)) {
                    InputStream inputStream = file.getInputStream(jarEntry);
                    try {
                        CtClass ctClass = mClassPool.makeClass(inputStream)
                        //获得jar中的类路径
                        mClassPool.insertClassPath(new ClassClassPath(ctClass.getClass()));
                        println("process success")
                    } catch (EOFException e) {
//                    e.printStackTrace()
                        println("process failed")
                    }
                }

            }
//            if (jarFile.exists()) {
//                jarFile.delete()
//            }
        }

    }
    private boolean shouldProcessClassInJar(String entryName) {
        return entryName.endsWith(".class") && !entryName.contains("android/support/")
    }
    private boolean  shouldProcess(final Object[] annotations) {
        Annotation annotation;
        boolean  include = false;
        for (Object iter : annotations) {
            annotation = (Annotation)iter
            //使用反射
            println("annotation")
            if (PATCH.equals(annotation.getClass().name)) {
                try {
                    Method method = annotation.class.getDeclaredMethod("included")
                    include = (Boolean)method.invoke(annotation)
                    println("annotation"+method.name)
                } catch (Exception e) {
                    e.printStackTrace()
                    println("annotation"+e.toString())
                }
            }

        }
        return include;
    }
}