package fr.adrienbrault.idea.symfony2plugin.doctrine.metadata.driver;

import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.stubs.indexes.expectedArguments.PhpExpectedFunctionArgument;
import com.jetbrains.php.lang.psi.stubs.indexes.expectedArguments.PhpExpectedFunctionClassConstantArgument;
import fr.adrienbrault.idea.symfony2plugin.doctrine.dict.DoctrineModelField;
import fr.adrienbrault.idea.symfony2plugin.doctrine.metadata.dict.DoctrineMetadataModel;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * example:
 *  - "#[Column(type: "decimal", precision: 2, scale: 1)]"
 *
 * @link https://www.doctrine-project.org/projects/doctrine-orm/en/2.9/reference/attributes-reference.html#attrref_table
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class DoctrinePhpAttributeMappingDriver implements DoctrineMappingDriverInterface {
    @Override
    public DoctrineMetadataModel getMetadata(@NotNull DoctrineMappingDriverArguments arguments) {
        PsiFile psiFile = arguments.getPsiFile();
        if(!(psiFile instanceof PhpFile)) {
            return null;
        }

        Collection<DoctrineModelField> fields = new ArrayList<>();
        DoctrineMetadataModel model = new DoctrineMetadataModel(fields);

        for (PhpClass phpClass : PhpElementsUtil.getClassesInterface(arguments.getProject(), arguments.getClassName())) {
            for (PhpAttribute attribute : phpClass.getAttributes()) {
                String fqn = attribute.getFQN();
                if (fqn == null) {
                    continue;
                }

                if (!PhpElementsUtil.isEqualClassName(fqn, "\\Doctrine\\ORM\\Mapping\\Table")) {
                    continue;
                }

                String name = PhpElementsUtil.findAttributeArgumentByNameAsString("name", attribute);
                if (name != null) {
                    model.setTable(name);
                }
            }

            Map<String, Map<String, String>> maps = new HashMap<>();
            for(Field field: phpClass.getFields()) {
                if (field.isConstant()) {
                    continue;
                }

                DoctrineModelField doctrineModelField = new DoctrineModelField(field.getName());
                doctrineModelField.addTarget(field);

                boolean isField = false;
                for (PhpAttribute attribute : field.getAttributes()) {
                    String fqn = attribute.getFQN();
                    if (fqn == null) {
                        continue;
                    }

                    if (PhpElementsUtil.isEqualClassName(fqn, "\\Doctrine\\ORM\\Mapping\\Column")) {
                        isField = true;

                        String name = PhpElementsUtil.findAttributeArgumentByNameAsString("name", attribute);
                        if (name != null) {
                            doctrineModelField.setColumn(name);
                        }

                        String type = PhpElementsUtil.findAttributeArgumentByNameAsString("type", attribute);
                        if (type != null) {
                            doctrineModelField.setTypeName(type);
                        }
                    }

                    if (PhpElementsUtil.isEqualClassName(fqn, "\\Doctrine\\ORM\\Mapping\\OneToOne", "\\Doctrine\\ORM\\Mapping\\ManyToOne", "\\Doctrine\\ORM\\Mapping\\OneToMany", "\\Doctrine\\ORM\\Mapping\\ManyToMany")) {
                        isField = true;

                        String substring = fqn.substring(fqn.lastIndexOf("\\") + 1);
                        doctrineModelField.setRelationType(substring);

                        PhpExpectedFunctionArgument argument = PhpElementsUtil.findAttributeArgumentByName("targetEntity", attribute);
                        if (argument instanceof PhpExpectedFunctionClassConstantArgument) {
                            String repositoryClassRaw = ((PhpExpectedFunctionClassConstantArgument) argument).getClassFqn();
                            if (StringUtils.isNotBlank(repositoryClassRaw)) {
                                doctrineModelField.setRelation(repositoryClassRaw);
                            }
                        }
                    }
                }

                if (isField) {
                    fields.add(doctrineModelField);
                }
            }
        }

        return model;
    }
}
